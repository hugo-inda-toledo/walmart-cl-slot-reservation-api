package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;

/** Clave de idempotencia entregada por el cliente: 1–128 caracteres. */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("idempotency key length must be between 1 and " + MAX_LENGTH);
        }
    }
}
