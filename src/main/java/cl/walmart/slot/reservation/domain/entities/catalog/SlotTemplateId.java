package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de plantilla de ventana en el catálogo.
 */
public record SlotTemplateId(UUID value) {

    public SlotTemplateId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
