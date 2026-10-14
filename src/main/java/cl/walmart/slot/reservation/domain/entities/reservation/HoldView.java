package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import java.time.Instant;
import java.util.UUID;

/** Vista de salida (serializable) de una retención; {@code reservationId} es nulo si no fue confirmada. */
public record HoldView(UUID holdId, String orderId, HoldStatus status, Instant createdAt, Instant expiresAt,
                       SlotSnapshotView slot, UUID reservationId) {
}
