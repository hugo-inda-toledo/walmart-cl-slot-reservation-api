package cl.walmart.slot.reservation.domain.exception.reservation;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import cl.walmart.slot.reservation.domain.exception.ErrorCode;

/** Códigos de error del contexto reservation, con su categoría HTTP-agnóstica. */
public enum ReservationErrorCode implements ErrorCode {
    INVALID_CUSTOMER_ID(ErrorCategory.INVALID_INPUT),
    SLOT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    SLOT_NO_SERVICE(ErrorCategory.BUSINESS_RULE),
    SLOT_CUTOFF_PASSED(ErrorCategory.BUSINESS_RULE),
    SLOT_FULL(ErrorCategory.CONFLICT),
    ACTIVE_HOLD_EXISTS(ErrorCategory.CONFLICT),
    ORDER_ALREADY_RESERVED(ErrorCategory.CONFLICT),
    HOLD_NOT_FOUND(ErrorCategory.NOT_FOUND),
    HOLD_EXPIRED(ErrorCategory.CONFLICT),
    HOLD_NOT_ACTIVE(ErrorCategory.CONFLICT),
    RESERVATION_NOT_FOUND(ErrorCategory.NOT_FOUND),
    RESERVATION_CANCELLATION_CLOSED(ErrorCategory.BUSINESS_RULE),
    IDEMPOTENCY_KEY_REUSED(ErrorCategory.BUSINESS_RULE),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(ErrorCategory.CONFLICT);

    private final ErrorCategory category;

    ReservationErrorCode(ErrorCategory category) {
        this.category = category;
    }

    public ErrorCategory category() {
        return category;
    }

    @Override
    public String value() {
        return name();
    }
}
