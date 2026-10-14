package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Ventana alternativa sugerida cuando la solicitada está agotada. */
public record AlternativeSlot(SlotId slotId, LocalDate deliveryDate, Instant startAt, Instant endAt, long priceClp,
                              int remainingCapacity) {

    public AlternativeSlot {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(deliveryDate, "deliveryDate must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
        if (priceClp < 0 || remainingCapacity < 0) {
            throw new IllegalArgumentException("priceClp and remainingCapacity must not be negative");
        }
    }
}
