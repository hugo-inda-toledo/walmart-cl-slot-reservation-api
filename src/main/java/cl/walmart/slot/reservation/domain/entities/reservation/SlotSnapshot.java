package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Copia inmutable de los datos de la ventana al momento de retenerla. */
public record SlotSnapshot(SlotId slotId, UUID zoneId, LocalDate deliveryDate, Instant startAt, Instant endAt,
                           Instant cutoffAt, long priceClp) {

    public SlotSnapshot {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(deliveryDate, "deliveryDate must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
        Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must be before endAt");
        }
        if (cutoffAt.isAfter(startAt)) {
            throw new IllegalArgumentException("cutoffAt must not be after startAt");
        }
        if (priceClp < 0) {
            throw new IllegalArgumentException("priceClp must not be negative");
        }
    }

    public ZoneDay zoneDay() {
        return new ZoneDay(zoneId, deliveryDate);
    }
}
