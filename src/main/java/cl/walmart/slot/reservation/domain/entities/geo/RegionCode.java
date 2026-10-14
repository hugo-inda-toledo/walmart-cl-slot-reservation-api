package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import java.util.regex.Pattern;

/**
 * Código de región (2 dígitos del CUT).
 */
public record RegionCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9]{2}");

    public RegionCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw GeoException.regionNotFound(value);
        }
    }
}
