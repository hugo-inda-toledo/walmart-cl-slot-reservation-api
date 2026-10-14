package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

/** Vista de salida (serializable) de una reserva; {@code cancelledAt} es nulo si no fue cancelada. */
public record ReservationView(UUID reservationId, UUID holdId, String orderId, ReservationStatus status,
                              Instant confirmedAt, Instant cancelledAt, SlotSnapshotView slot) {
}
