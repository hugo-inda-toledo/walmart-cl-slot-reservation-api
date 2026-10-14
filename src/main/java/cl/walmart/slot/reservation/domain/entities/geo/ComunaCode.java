package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import java.util.regex.Pattern;

/**
 * Código único territorial (CUT) de una comuna: 5 dígitos con prefijos de provincia y región.
 */
public record ComunaCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9]{5}");

    public ComunaCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw GeoException.unknownComuna(value);
        }
    }

    public RegionCode regionCode() {
        return new RegionCode(value.substring(0, 2));
    }

    public ProvinceCode provinceCode() {
        return new ProvinceCode(value.substring(0, 3));
    }
}
