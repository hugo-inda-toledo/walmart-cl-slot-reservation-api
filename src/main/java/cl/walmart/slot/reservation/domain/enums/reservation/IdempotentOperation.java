package cl.walmart.slot.reservation.domain.enums.reservation;

/** Operaciones que aceptan clave de idempotencia. */
public enum IdempotentOperation {
    HOLD_SLOT,
    CONFIRM_HOLD
}
