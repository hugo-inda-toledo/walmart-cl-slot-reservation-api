package cl.walmart.slot.reservation.support.reservation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fixture de integración de reservas: crea ventanas aisladas en la base compartida de Testcontainers y limpia todo
 * lo que creó.
 *
 * <p>Aislamiento: cada ventana cuelga de una plantilla {@code INACTIVE} propia (la generación del catálogo la
 * ignora) y vive en una banda de fechas desde hoy (America/Santiago, según la base) + {@value #BAND_START_DAYS}
 * días, fuera del horizonte máximo de generación (60 días) y de las fechas fijas de los IT de catalog
 * (2030–2037), sobre la zona demo {@link #ZONE_ID} sembrada por geo. Todos los instantes se calculan con
 * {@code now()} de la base.
 *
 * <p>Uso: una instancia por clase de test y {@link #cleanup()} en {@code @AfterEach} o {@code @AfterAll}.
 */
public final class ReservationTestSlots {

    /** Zona demo SANTIAGO_ORIENTE de {@code V2_4__seed_delivery_zones.sql}. */
    public static final UUID ZONE_ID = UUID.fromString("5f1c0e2a-0000-4000-8000-000000001302");
    public static final int BAND_START_DAYS = 100;
    public static final String TIME_ZONE = "America/Santiago";

    /** Segundo de inicio de la próxima plantilla: único por JVM; dura 1 s, así nunca choca con las sembradas. */
    private static final AtomicInteger TEMPLATE_SECOND =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(1, 40_000));

    private final JdbcTemplate jdbc;
    private final Set<UUID> templates = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<UUID> slots = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<UUID> holidays = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> customers = Collections.synchronizedSet(new LinkedHashSet<>());

    public ReservationTestSlots(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** Ventana de capacidad dada en el primer día de la banda, de 10:00 a 12:00 y cierre 2 horas antes. */
    public TestSlot createSlot(int capacity) {
        return slot().capacity(capacity).create();
    }

    public SlotSpec slot() {
        return new SlotSpec();
    }

    /** Fecha de negocio de la banda: hoy en America/Santiago según la base + {@value #BAND_START_DAYS} + offset. */
    public LocalDate bandDate(int dayOffset) {
        requireNonNegative(dayOffset, "dayOffset");
        return jdbc.queryForObject("select (now() at time zone '" + TIME_ZONE + "')::date + ?",
                LocalDate.class, BAND_START_DAYS + dayOffset);
    }

    /** Bloqueo de la zona en una fecha de la banda: las ventanas de ese día quedan {@code NO_SERVICE}. */
    public LocalDate blockZoneOn(int dayOffset) {
        LocalDate date = bandDate(dayOffset);
        UUID id = jdbc.queryForObject("insert into catalog_holiday (holiday_date, name, zone_id) "
                + "values (?, 'Bloqueo de prueba de reservas', ?) returning id", UUID.class, date, ZONE_ID);
        holidays.add(id);
        return date;
    }

    /** Identificador de cliente único registrado para la limpieza de sus claves, reservas y retenciones. */
    public String newCustomerId() {
        String customerId = "rsv-it-" + UUID.randomUUID().toString().substring(0, 13);
        customers.add(customerId);
        return customerId;
    }

    public String newOrderId() {
        return "order-" + UUID.randomUUID().toString().substring(0, 13);
    }

    public Instant dbNow() {
        return jdbc.queryForObject("select now()", OffsetDateTime.class).toInstant();
    }

    public List<UUID> slotIds() {
        synchronized (slots) {
            return List.copyOf(slots);
        }
    }

    /**
     * Borra, en orden inverso a las FK, las claves de idempotencia de los clientes registrados y las reservas y
     * retenciones de esos clientes o de las ventanas creadas; luego las ventanas, plantillas y feriados propios.
     */
    public void cleanup() {
        String[] slotIds = ids(slots);
        String[] customerIds = customers.toArray(String[]::new);
        jdbc.update("delete from reservation_idempotency_key where customer_id = any (?)", (Object) customerIds);
        jdbc.update("delete from reservation_booking where slot_id = any (?::uuid[]) or customer_id = any (?)",
                slotIds, customerIds);
        jdbc.update("delete from reservation_slot_hold where slot_id = any (?::uuid[]) or customer_id = any (?)",
                slotIds, customerIds);
        jdbc.update("delete from catalog_delivery_slot where id = any (?::uuid[])", (Object) slotIds);
        jdbc.update("delete from catalog_slot_template where id = any (?::uuid[])", (Object) ids(templates));
        jdbc.update("delete from catalog_holiday where id = any (?::uuid[])", (Object) ids(holidays));
        slots.clear();
        templates.clear();
        holidays.clear();
        customers.clear();
    }

    private TestSlot insert(SlotSpec spec) {
        int second = TEMPLATE_SECOND.getAndIncrement() % 86_000 + 1;
        UUID templateId = UUID.randomUUID();
        jdbc.update("insert into catalog_slot_template (id, zone_id, day_of_week, start_time, end_time, capacity, "
                + "price_clp, service_type, status) values (?, ?, ?, time '00:00' + make_interval(secs => ?), "
                + "time '00:00' + make_interval(secs => ? + 1), ?, ?, 'HOME_DELIVERY', 'INACTIVE')",
                templateId, ZONE_ID, second % 7 + 1, second, second, spec.capacity, spec.priceClp);
        templates.add(templateId);

        LocalDate date = bandDate(spec.dayOffset);
        String startAt = "((?::date + ?::time) at time zone '" + TIME_ZONE + "')";
        String cutoffAt = spec.cutoffFromDbNow == null
                ? startAt + " - make_interval(secs => ?)"
                : "now() + make_interval(secs => ?)";
        Object cutoffSeconds = spec.cutoffFromDbNow == null
                ? spec.cutoffLeadTime.toMillis() / 1000.0
                : spec.cutoffFromDbNow.toMillis() / 1000.0;
        List<Object> args = new ArrayList<>(List.of(templateId, ZONE_ID, date,
                date, spec.startTime.toString(),
                date, spec.startTime.toString(), spec.duration.toMillis() / 1000.0));
        if (spec.cutoffFromDbNow == null) {
            args.addAll(List.of(date, spec.startTime.toString()));
        }
        args.add(cutoffSeconds);
        args.add(spec.priceClp);
        args.add(spec.capacity);
        UUID slotId = jdbc.queryForObject("insert into catalog_delivery_slot (id, template_id, zone_id, "
                + "delivery_date, service_type, start_at, end_at, cutoff_at, price_clp, capacity) values "
                + "(gen_random_uuid(), ?, ?, ?, 'HOME_DELIVERY', " + startAt + ", " + startAt
                + " + make_interval(secs => ?), " + cutoffAt + ", ?, ?) returning id", UUID.class, args.toArray());
        slots.add(slotId);
        return jdbc.queryForObject("select id, zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, "
                + "capacity from catalog_delivery_slot where id = ?", (rs, row) -> new TestSlot(
                rs.getObject("id", UUID.class), rs.getObject("zone_id", UUID.class),
                rs.getObject("delivery_date", LocalDate.class),
                rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                rs.getObject("end_at", OffsetDateTime.class).toInstant(),
                rs.getObject("cutoff_at", OffsetDateTime.class).toInstant(),
                rs.getLong("price_clp"), rs.getInt("capacity")), slotId);
    }

    private static String[] ids(Set<UUID> source) {
        synchronized (source) {
            return source.stream().map(UUID::toString).toArray(String[]::new);
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    /** Ventana creada por el fixture, tal como quedó en la base. */
    public record TestSlot(UUID id, UUID zoneId, LocalDate deliveryDate, Instant startAt, Instant endAt,
                           Instant cutoffAt, long priceClp, int capacity) {
    }

    /** Parámetros de una ventana aislada; los instantes se resuelven con la hora de la base al crearla. */
    public final class SlotSpec {
        private int capacity = 5;
        private int dayOffset;
        private LocalTime startTime = LocalTime.of(10, 0);
        private Duration duration = Duration.ofHours(2);
        private Duration cutoffLeadTime = Duration.ofHours(2);
        private Duration cutoffFromDbNow;
        private long priceClp = 2990;

        private SlotSpec() {
        }

        public SlotSpec capacity(int value) {
            requireNonNegative(value, "capacity");
            capacity = value;
            return this;
        }

        /** Día dentro de la banda (0 = primer día). */
        public SlotSpec dayOffset(int value) {
            requireNonNegative(value, "dayOffset");
            dayOffset = value;
            return this;
        }

        /** Hora local (America/Santiago) de inicio y duración de la ventana. */
        public SlotSpec startingAt(LocalTime start, Duration length) {
            startTime = Objects.requireNonNull(start, "start");
            if (length.isNegative() || length.isZero()) {
                throw new IllegalArgumentException("length must be positive");
            }
            duration = length;
            return this;
        }

        /** Cierre {@code lead} antes del inicio (default 2 horas). */
        public SlotSpec cutoffBeforeStart(Duration lead) {
            requireNonNegative(lead.toMillis(), "lead");
            cutoffLeadTime = lead;
            cutoffFromDbNow = null;
            return this;
        }

        /** Cierre relativo a {@code now()} de la base: negativo = ya cerrada, positivo = cierra pronto. */
        public SlotSpec cutoffFromDbNow(Duration offset) {
            cutoffFromDbNow = Objects.requireNonNull(offset, "offset");
            return this;
        }

        public SlotSpec priceClp(long value) {
            requireNonNegative(value, "priceClp");
            priceClp = value;
            return this;
        }

        public TestSlot create() {
            return insert(this);
        }
    }
}
