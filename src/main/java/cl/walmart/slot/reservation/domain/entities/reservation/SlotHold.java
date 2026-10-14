package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * Retención provisoria de un cupo. {@code closedAt} es nulo si y solo si el estado persistido es {@code ACTIVE}.
 */
public record SlotHold(HoldId id, CustomerId customerId, OrderId orderId, SlotSnapshot slot, HoldStatus status,
                       Instant createdAt, Instant expiresAt, Instant closedAt) {

    public SlotHold {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if ((status == HoldStatus.ACTIVE) != (closedAt == null)) {
            throw new IllegalArgumentException("closedAt must be null if and only if the hold is ACTIVE");
        }
    }

    /**
     * Estado informado al cliente: una retención {@code ACTIVE} con vencimiento alcanzado según la hora de la base
     * de datos se informa {@code EXPIRED}; los demás estados se informan tal cual.
     */
    public HoldStatus effectiveStatus(Instant dbNow) {
        Objects.requireNonNull(dbNow, "dbNow must not be null");
        if (status == HoldStatus.ACTIVE && !expiresAt.isAfter(dbNow)) {
            return HoldStatus.EXPIRED;
        }
        return status;
    }
}
