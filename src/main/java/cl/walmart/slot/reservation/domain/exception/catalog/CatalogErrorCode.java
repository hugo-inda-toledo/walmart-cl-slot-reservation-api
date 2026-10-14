package cl.walmart.slot.reservation.domain.exception.catalog;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import cl.walmart.slot.reservation.domain.exception.ErrorCode;

/**
 * Códigos de error del contexto catalog, con su categoría HTTP-agnóstica.
 */
public enum CatalogErrorCode implements ErrorCode {
    ADDRESS_NOT_COVERED(ErrorCategory.BUSINESS_RULE),
    DELIVERY_DATE_OUT_OF_RANGE(ErrorCategory.INVALID_INPUT);

    private final ErrorCategory category;

    CatalogErrorCode(ErrorCategory category) {
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
