package cl.walmart.slot.reservation.domain.exception.geo;

import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.Map;

/**
 * Falla de dominio del contexto geo. Se crea mediante las fábricas por código de error.
 */
public final class GeoException extends DomainException {

    private GeoException(GeoErrorCode code, String message, Map<String, Object> details) {
        super(code, code.category(), message, details);
    }

    public static GeoException regionNotFound(String regionCode) {
        return new GeoException(GeoErrorCode.REGION_NOT_FOUND, "La región indicada no existe.",
                detail("regionCode", regionCode));
    }

    /** Código de provincia mal formado; no hay un código de error propio, se informa como región inexistente. */
    public static GeoException invalidProvinceCode(String provinceCode) {
        return new GeoException(GeoErrorCode.REGION_NOT_FOUND, "El código de provincia indicado no es válido.",
                detail("provinceCode", provinceCode));
    }

    public static GeoException zoneNotFound(DeliveryZoneId zoneId) {
        return new GeoException(GeoErrorCode.ZONE_NOT_FOUND, "La zona de despacho indicada no existe.",
                detail("zoneId", zoneId == null ? null : zoneId.value().toString()));
    }

    public static GeoException unknownComuna(String comunaCode) {
        return new GeoException(GeoErrorCode.UNKNOWN_COMUNA, "La comuna indicada no existe.",
                detail("comunaCode", comunaCode));
    }

    public static GeoException incompleteCoordinates() {
        return new GeoException(GeoErrorCode.INCOMPLETE_COORDINATES,
                "Debe indicar latitud y longitud juntas, o ninguna de las dos.", Map.of());
    }

    public static GeoException invalidCoordinates() {
        return new GeoException(GeoErrorCode.INVALID_COORDINATES,
                "Las coordenadas están fuera de rango o no son números finitos.", Map.of());
    }

    private static Map<String, Object> detail(String key, String value) {
        return value == null ? Map.of() : Map.of(key, value);
    }
}
