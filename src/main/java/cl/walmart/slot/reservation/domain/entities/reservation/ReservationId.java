package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;
import java.util.UUID;

/** Identificador UUID de la reserva. */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /** Genera un UUID v4 en la aplicación: los identificadores no dependen del reloj. */
    public static ReservationId random() {
        return new ReservationId(UUID.randomUUID());
    }
}
