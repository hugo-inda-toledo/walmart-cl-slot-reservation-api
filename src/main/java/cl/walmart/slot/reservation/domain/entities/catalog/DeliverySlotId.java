package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de ventana de despacho en el catálogo.
 */
public record DeliverySlotId(UUID value) {

    public DeliverySlotId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
