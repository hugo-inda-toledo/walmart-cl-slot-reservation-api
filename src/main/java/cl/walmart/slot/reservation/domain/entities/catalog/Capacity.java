package cl.walmart.slot.reservation.domain.entities.catalog;

/**
 * Capacidad de una ventana: entero no negativo; cero es un bloque sin cupos.
 */
public record Capacity(int value) {

    public Capacity {
        if (value < 0) {
            throw new IllegalArgumentException("capacity must not be negative: " + value);
        }
    }
}
