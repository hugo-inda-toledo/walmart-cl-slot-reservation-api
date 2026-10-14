package cl.walmart.slot.reservation.domain.entities.geo;

import java.util.Objects;

/**
 * Comuna del catálogo DPA; {@code hasBoundary} indica si se dispone de su límite geográfico.
 */
public record Comuna(ComunaCode code, String name, ProvinceCode provinceCode, String provinceName,
                     boolean hasBoundary) {

    public Comuna {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(provinceCode, "provinceCode must not be null");
        Objects.requireNonNull(provinceName, "provinceName must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public RegionCode regionCode() {
        return code.regionCode();
    }
}
