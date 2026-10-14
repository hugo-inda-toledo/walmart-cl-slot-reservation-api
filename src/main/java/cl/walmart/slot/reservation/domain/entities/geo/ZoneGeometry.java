package cl.walmart.slot.reservation.domain.entities.geo;

import java.util.Objects;

/**
 * Geometría de una zona como texto GeoJSON (MultiPolygon, SRID 4326).
 */
public record ZoneGeometry(String geoJson) {

    public ZoneGeometry {
        Objects.requireNonNull(geoJson, "geoJson must not be null");
        if (geoJson.isBlank()) {
            throw new IllegalArgumentException("geoJson must not be blank");
        }
    }
}
