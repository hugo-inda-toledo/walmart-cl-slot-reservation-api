package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import java.util.Objects;

/** Retener una ventana para una orden. El cliente no viaja en el comando: lo resuelve el puerto de identidad. */
public record HoldSlotCommand(SlotId slotId, OrderId orderId, IdempotencyKey key) {

    public HoldSlotCommand {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(key, "key must not be null");
    }
}
