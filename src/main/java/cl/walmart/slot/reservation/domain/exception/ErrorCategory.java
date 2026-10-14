package cl.walmart.slot.reservation.domain.exception;

/**
 * The HTTP-agnostic classification of a domain error.
 */
public enum ErrorCategory {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    BUSINESS_RULE,
    UNAVAILABLE
}
