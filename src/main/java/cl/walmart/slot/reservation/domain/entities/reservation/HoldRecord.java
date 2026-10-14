package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Retención leída junto con la reserva que la confirmó (si existe) y la hora de la base de datos. */
public record HoldRecord(SlotHold hold, Optional<ReservationId> reservationId, Instant dbNow) {

    public HoldRecord {
        Objects.requireNonNull(hold, "hold must not be null");
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(dbNow, "dbNow must not be null");
    }
}
