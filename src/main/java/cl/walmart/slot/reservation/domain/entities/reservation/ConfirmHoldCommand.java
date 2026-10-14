package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import java.util.Objects;

/** Confirmar una retención como reserva. El cliente no viaja en el comando. */
public record ConfirmHoldCommand(HoldId holdId, IdempotencyKey key) {

    public ConfirmHoldCommand {
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(key, "key must not be null");
    }
}
