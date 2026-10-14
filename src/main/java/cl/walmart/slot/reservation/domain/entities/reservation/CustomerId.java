package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identificador opaco del cliente: 1–64 caracteres alfanuméricos, punto, guion o guion bajo. */
public record CustomerId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    public CustomerId {
        Objects.requireNonNull(value, "value must not be null");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("customer id must match ^[A-Za-z0-9._-]{1,64}$");
        }
    }
}
