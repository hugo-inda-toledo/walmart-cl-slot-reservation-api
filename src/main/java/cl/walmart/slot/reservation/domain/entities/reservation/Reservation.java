package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import java.time.Instant;
import java.util.Objects;

/** Reserva confirmada de una ventana; {@code cancelledAt} es no nulo si y solo si está {@code CANCELLED}. */
public record Reservation(ReservationId id, HoldId holdId, CustomerId customerId, OrderId orderId,
                          SlotSnapshot slot, ReservationStatus status, Instant confirmedAt, Instant cancelledAt) {

    public Reservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        if ((status == ReservationStatus.CANCELLED) != (cancelledAt != null)) {
            throw new IllegalArgumentException("cancelledAt must be set if and only if the reservation is CANCELLED");
        }
    }
}
