package cl.walmart.slot.reservation.domain.exception.catalog;

import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Falla de dominio del contexto catalog. Se crea mediante las fábricas por código de error.
 */
public final class CatalogException extends DomainException {

    private CatalogException(CatalogErrorCode code, String message, Map<String, Object> details) {
        super(code, code.category(), message, details);
    }

    /** Motivo y comuna son opcionales: solo se incluyen en los detalles cuando no son {@code null}. */
    public static CatalogException addressNotCovered(String reason, String comunaCode) {
        var details = new HashMap<String, Object>();
        if (reason != null) {
            details.put("reason", reason);
        }
        if (comunaCode != null) {
            details.put("comunaCode", comunaCode);
        }
        return new CatalogException(CatalogErrorCode.ADDRESS_NOT_COVERED,
                "La dirección no tiene cobertura de despacho.", details);
    }

    public static CatalogException deliveryDateOutOfRange(LocalDate date, LocalDate minDate, LocalDate maxDate) {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(minDate, "minDate must not be null");
        Objects.requireNonNull(maxDate, "maxDate must not be null");
        return new CatalogException(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE,
                "La fecha solicitada está fuera del período consultable.",
                Map.of("date", date.toString(), "minDate", minDate.toString(), "maxDate", maxDate.toString()));
    }
}
