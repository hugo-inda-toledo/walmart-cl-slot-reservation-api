package cl.walmart.slot.reservation.domain.exception.reservation;

import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.Map;

/**
 * La misma clave está siendo procesada por otra transacción y la espera se agotó. Revierte y no se fija; la
 * infraestructura responde 409 con {@code Retry-After}.
 */
public final class IdempotencyInProgressException extends DomainException {

    private final int retryAfterSeconds;

    public IdempotencyInProgressException(int retryAfterSeconds) {
        super(ReservationErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                ReservationErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.category(),
                "La petición con esta clave de idempotencia sigue en curso.",
                Map.of("retryAfterSeconds", retryAfterSeconds));
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
