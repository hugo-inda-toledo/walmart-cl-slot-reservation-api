package cl.walmart.slot.reservation.domain.entities.geo;

import java.util.Objects;

/**
 * Nodo de despacho (centro de distribución o tienda) que atiende una zona.
 */
public record DispatchNode(String code, String name) {

    public DispatchNode {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (code.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("code and name must not be blank");
        }
    }
}
