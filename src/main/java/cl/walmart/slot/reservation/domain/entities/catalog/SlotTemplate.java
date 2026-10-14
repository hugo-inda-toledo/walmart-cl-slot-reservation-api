package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.service.catalog.CutoffPolicy;
import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Plantilla de bloque horario de una zona para un día de la semana. Es configuración (datos semilla): decide si
 * aplica a una fecha y produce la ventana concreta con instantes en {@code America/Santiago}.
 */
public final class SlotTemplate {

    private final SlotTemplateId id;
    private final CatalogZoneId zoneId;
    private final DayOfWeek dayOfWeek;
    private final TimeRange timeRange;
    private final Capacity capacity;
    private final Money price;
    private final ServiceType serviceType;
    private final boolean active;

    public SlotTemplate(SlotTemplateId id, CatalogZoneId zoneId, DayOfWeek dayOfWeek, TimeRange timeRange,
            Capacity capacity, Money price, ServiceType serviceType, boolean active) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        this.timeRange = Objects.requireNonNull(timeRange, "timeRange must not be null");
        this.capacity = Objects.requireNonNull(capacity, "capacity must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType must not be null");
        this.active = active;
    }

    public SlotTemplateId id() {
        return id;
    }

    public CatalogZoneId zoneId() {
        return zoneId;
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public TimeRange timeRange() {
        return timeRange;
    }

    public Capacity capacity() {
        return capacity;
    }

    public Money price() {
        return price;
    }

    public ServiceType serviceType() {
        return serviceType;
    }

    public boolean active() {
        return active;
    }

    /** La plantilla aplica a una fecha cuando el día de la semana coincide (feriados y estado se evalúan fuera). */
    public boolean appliesTo(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return date.getDayOfWeek() == dayOfWeek;
    }

    /**
     * Crea la ventana de la fecha dada. La hora local se convierte con {@code ZonedDateTime.of}: una hora
     * inexistente se desplaza hacia adelante por la duración del salto y una ambigua toma su primera ocurrencia.
     * El cierre propuesto por la política nunca queda después del inicio.
     *
     * @throws IllegalArgumentException si la fecha no corresponde al día de la plantilla o si un salto de horario
     *         desplaza el inicio y lo colapsa con el término
     */
    public NewDeliverySlot instantiate(LocalDate date, CutoffPolicy cutoff) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (!appliesTo(date)) {
            throw new IllegalArgumentException(
                    "template " + id.value() + " does not apply to " + date + " (" + date.getDayOfWeek() + ")");
        }
        var startAt = toInstant(date, timeRange.start());
        var endAt = toInstant(date, timeRange.end());
        var proposed = cutoff.cutoffFor(this, startAt);
        var cutoffAt = proposed.isAfter(startAt) ? startAt : proposed;
        return new NewDeliverySlot(id, zoneId, date, serviceType, startAt, endAt, cutoffAt, price, capacity);
    }

    private static Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, CatalogTime.BUSINESS_ZONE).toInstant();
    }
}
