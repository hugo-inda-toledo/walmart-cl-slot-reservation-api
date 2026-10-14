package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;
import java.util.UUID;

/** Identificador UUID de la retención. */
public record HoldId(UUID value) {

    public HoldId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /** Genera un UUID v4 en la aplicación: los identificadores no dependen del reloj. */
    public static HoldId random() {
        return new HoldId(UUID.randomUUID());
    }
}
