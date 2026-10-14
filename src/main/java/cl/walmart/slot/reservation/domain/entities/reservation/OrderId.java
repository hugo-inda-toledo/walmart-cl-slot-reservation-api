package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identificador opaco de la orden: 1–64 caracteres alfanuméricos, punto, guion o guion bajo. */
public record OrderId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    public OrderId {
        Objects.requireNonNull(value, "value must not be null");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("order id must match ^[A-Za-z0-9._-]{1,64}$");
        }
    }
}
