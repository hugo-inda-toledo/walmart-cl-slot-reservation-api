package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;
import java.util.UUID;

/** Identificador UUID de la ventana. */
public record SlotId(UUID value) {

    public SlotId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
