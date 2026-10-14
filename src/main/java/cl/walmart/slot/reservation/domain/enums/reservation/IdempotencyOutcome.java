package cl.walmart.slot.reservation.domain.enums.reservation;

/** Resultado fijado en una clave de idempotencia. */
public enum IdempotencyOutcome {
    SUCCEEDED,
    REJECTED
}
