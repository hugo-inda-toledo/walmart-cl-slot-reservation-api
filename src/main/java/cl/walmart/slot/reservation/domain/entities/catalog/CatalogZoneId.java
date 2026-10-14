package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de zona de despacho en el catálogo.
 */
public record CatalogZoneId(UUID value) {

    public CatalogZoneId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
