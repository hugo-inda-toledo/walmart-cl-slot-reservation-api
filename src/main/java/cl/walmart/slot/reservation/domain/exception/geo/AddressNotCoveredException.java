package cl.walmart.slot.reservation.domain.exception.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.HashMap;
import java.util.Optional;

/**
 * La dirección no tiene cobertura de despacho; incluye el motivo y la comuna resuelta, si existe.
 */
public final class AddressNotCoveredException extends DomainException {

    public AddressNotCoveredException(NotCoveredReason reason, Optional<ComunaCode> comunaCode) {
        super(GeoErrorCode.ADDRESS_NOT_COVERED, GeoErrorCode.ADDRESS_NOT_COVERED.category(),
                "La dirección no tiene cobertura de despacho.", detailsOf(reason, comunaCode));
    }

    private static java.util.Map<String, Object> detailsOf(NotCoveredReason reason, Optional<ComunaCode> comunaCode) {
        var details = new HashMap<String, Object>();
        details.put("reason", reason.name());
        comunaCode.ifPresent(code -> details.put("comunaCode", code.value()));
        return details;
    }
}
