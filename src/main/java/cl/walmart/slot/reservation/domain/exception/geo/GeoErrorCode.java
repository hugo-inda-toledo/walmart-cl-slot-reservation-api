package cl.walmart.slot.reservation.domain.exception.geo;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import cl.walmart.slot.reservation.domain.exception.ErrorCode;

/**
 * Códigos de error del contexto geo, con su categoría HTTP-agnóstica.
 */
public enum GeoErrorCode implements ErrorCode {
    REGION_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ZONE_NOT_FOUND(ErrorCategory.NOT_FOUND),
    UNKNOWN_COMUNA(ErrorCategory.INVALID_INPUT),
    INCOMPLETE_COORDINATES(ErrorCategory.INVALID_INPUT),
    INVALID_COORDINATES(ErrorCategory.INVALID_INPUT),
    ADDRESS_NOT_COVERED(ErrorCategory.BUSINESS_RULE);

    private final ErrorCategory category;

    GeoErrorCode(ErrorCategory category) {
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
