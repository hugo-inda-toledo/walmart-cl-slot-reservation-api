package cl.walmart.slot.reservation.domain.entities.geo;

import java.util.Objects;

/**
 * Región de Chile con su posición en el orden norte a sur.
 */
public record Region(RegionCode code, String name, int northToSouthOrder) {

    public Region {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
