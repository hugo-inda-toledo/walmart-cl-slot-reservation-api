package cl.walmart.slot.reservation.domain.entities.geo;

/**
 * Distancia máxima, en metros, a la que un punto fuera de todo límite se asigna a la comuna más cercana.
 */
public record BorderTolerance(int meters) {

    public static final int MAX_METERS = 5000;

    public BorderTolerance {
        if (meters < 0 || meters > MAX_METERS) {
            throw new IllegalArgumentException("meters must be between 0 and " + MAX_METERS + ": " + meters);
        }
    }
}
