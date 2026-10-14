package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoException;

/**
 * Punto geográfico WGS84 con latitud en [-90, 90] y longitud en [-180, 180], ambas finitas.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw GeoException.invalidCoordinates();
        }
    }
}
