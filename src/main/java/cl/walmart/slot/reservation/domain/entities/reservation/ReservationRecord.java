package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.util.Objects;

/** Reserva leída junto con la hora de la base de datos. */
public record ReservationRecord(Reservation reservation, Instant dbNow) {

    public ReservationRecord {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(dbNow, "dbNow must not be null");
    }
}
