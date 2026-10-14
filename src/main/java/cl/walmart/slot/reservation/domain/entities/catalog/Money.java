package cl.walmart.slot.reservation.domain.entities.catalog;

/**
 * Monto en pesos chilenos (CLP): entero no negativo, sin decimales.
 */
public record Money(long amountClp) {

    public Money {
        if (amountClp < 0) {
            throw new IllegalArgumentException("amountClp must not be negative: " + amountClp);
        }
    }
}
