package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import java.util.regex.Pattern;

/**
 * Código de provincia (3 dígitos del CUT: región + 1 dígito).
 */
public record ProvinceCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9]{3}");

    public ProvinceCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw GeoException.invalidProvinceCode(value);
        }
    }

    public RegionCode regionCode() {
        return new RegionCode(value.substring(0, 2));
    }
}
