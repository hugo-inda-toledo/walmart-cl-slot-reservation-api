package cl.walmart.slot.reservation.domain.entities.geo;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una zona de despacho.
 */
public record DeliveryZoneId(UUID value) {

    public DeliveryZoneId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
