package cl.walmart.slot.reservation.integration.catalog.seam;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.usecase.catalog.ListSlotsForDateUseCase;
import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import cl.walmart.slot.reservation.support.IntegrationTest;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seam con reservas (requisitos 2.5, 9.5, 10.1, 10.2, 12.4) contra PostgreSQL y Valkey reales: el puerto de
 * consulta lee siempre de la base (los contadores que cambió reservas se ven aunque la caché tenga un valor
 * viejo) y la toma atómica de referencia respeta capacidad y cierre devolviendo zona y fecha.
 */
@IntegrationTest
@Import(SeamClockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotQueryPortSeamIT {

    private static final UUID SERENA_ZONE = UUID.fromString("5f1c0e2a-0000-4000-8000-000000000401");
    private static final LocalDate TODAY = LocalDate.of(2037, 3, 5);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final AddressQuery SERENA_ADDRESS = new AddressQuery("04101", null, null);

    @Autowired
    private SlotQueryPort port;
    @Autowired
    private ListSlotsForDateUseCase listSlots;
    @Autowired
    private GenerateSlotsUseCase generation;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void regenerateCleanWindows() {
        clearTestData();
        clock.setInstant(SeamClockConfiguration.TODAY_MORNING);
        generation.generate();
    }

    @AfterAll
    void clearTestData() {
        jdbc.update("delete from catalog_delivery_slot where delivery_date >= date '2037-01-01'");
        jdbc.update("delete from catalog_holiday where holiday_date >= date '2037-01-01'");
    }

    @Test
    void reflectsCountersChangedBySqlEvenWhenTheCacheHoldsAnOldValue() {
        var cached = listSlots.list(SERENA_ADDRESS, TOMORROW);   // llena la caché de la zona-fecha
        assertThat(cached.slots()).extracting(slot -> slot.status()).containsOnly(SlotStatus.AVAILABLE);
        UUID target = cached.slots().getFirst().slotId().value();

        jdbc.update("update catalog_delivery_slot set reserved = capacity where id = ?", target);

        assertThat(listSlots.list(SERENA_ADDRESS, TOMORROW).slots()).as("la caché sigue con el valor anterior")
                .extracting(slot -> slot.status()).containsOnly(SlotStatus.AVAILABLE);
        SlotView fresh = port.findSlot(target).orElseThrow();
        assertThat(fresh.status()).isEqualTo(SlotView.Status.SOLD_OUT);
        assertThat(fresh.reserved()).isEqualTo(fresh.capacity());
        assertThat(fresh.remainingCapacity()).isZero();
        assertThat(fresh.zoneId()).isEqualTo(SERENA_ZONE);
        assertThat(fresh.deliveryDate()).isEqualTo(TOMORROW);
    }

    @Test
    void availableSlotsExcludeSoldOutClosedAndHolidaySlots() {
        // agotado: el primer bloque de mañana; cerrado: el de hoy a las 16:00 (el reloj avanza a 14:30, pasado
        // su cierre de las 14:00); feriado: pasado mañana.
        List<UUID> tomorrow = ids(TOMORROW);
        jdbc.update("update catalog_delivery_slot set reserved = capacity where id = ?", tomorrow.getFirst());
        jdbc.update("insert into catalog_holiday (holiday_date, name) values (?, 'Feriado de prueba')", TODAY.plusDays(2));
        clock.setInstant(Instant.parse("2037-03-05T17:30:00Z"));

        List<SlotView> available = port.findAvailableSlots(SERENA_ZONE, TODAY, TODAY.plusDays(2));

        assertThat(available).extracting(SlotView::status).containsOnly(SlotView.Status.AVAILABLE);
        assertThat(available).filteredOn(view -> view.deliveryDate().equals(TODAY)).hasSize(3);     // 1 cerrado
        assertThat(available).filteredOn(view -> view.deliveryDate().equals(TOMORROW)).hasSize(3);  // 1 agotado
        assertThat(available).filteredOn(view -> view.deliveryDate().equals(TODAY.plusDays(2))).isEmpty(); // feriado
        assertThat(available).extracting(SlotView::startAt).isSorted();
    }

    @Test
    void atomicTakeReturnsZoneAndDateAndRespectsCapacityAndCutoff() {
        List<UUID> slots = ids(TOMORROW);
        String take = "update catalog_delivery_slot set held = held + 1"
                + " where id = ? and reserved + held < capacity and cutoff_at > now() returning zone_id, delivery_date";

        List<Map<String, Object>> taken = jdbc.queryForList(take, slots.get(0));
        assertThat(taken).hasSize(1);
        assertThat(taken.getFirst().get("zone_id")).isEqualTo(SERENA_ZONE);
        assertThat(taken.getFirst().get("delivery_date")).hasToString(TOMORROW.toString());
        assertThat(port.findSlot(slots.get(0)).orElseThrow().held()).isOne();

        jdbc.update("update catalog_delivery_slot set reserved = capacity - held where id = ?", slots.get(1));
        assertThat(jdbc.queryForList(take, slots.get(1))).as("agotada").isEmpty();

        jdbc.update("update catalog_delivery_slot set cutoff_at = now() - interval '1 hour' where id = ?", slots.get(2));
        assertThat(jdbc.queryForList(take, slots.get(2))).as("cerrada").isEmpty();
        assertThat(port.findSlot(slots.get(2)).orElseThrow().held()).isZero();
    }

    private List<UUID> ids(LocalDate date) {
        return jdbc.queryForList("select id from catalog_delivery_slot where zone_id = ? and delivery_date = ?"
                + " order by start_at", UUID.class, SERENA_ZONE, date);
    }
}
