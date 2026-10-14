package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import java.util.Objects;
import java.util.Optional;

/**
 * Dirección a resolver: calle y número (informativos), comuna declarada y ubicación opcional.
 */
public record Address(String street, String number, ComunaCode comunaCode, Optional<GeoPoint> location) {

    public Address {
        Objects.requireNonNull(comunaCode, "comunaCode must not be null");
        Objects.requireNonNull(location, "location must not be null");
    }

    /**
     * Construye una dirección desde datos crudos. Las coordenadas van ambas o ninguna.
     */
    public static Address of(String street, String number, String comunaCode, Double latitude, Double longitude) {
        var code = new ComunaCode(comunaCode);
        if ((latitude == null) != (longitude == null)) {
            throw GeoException.incompleteCoordinates();
        }
        var point = latitude == null ? Optional.<GeoPoint>empty() : Optional.of(new GeoPoint(latitude, longitude));
        return new Address(strip(street), strip(number), code, point);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
