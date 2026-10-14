package cl.walmart.slot.reservation.domain.port.geo;

import java.util.Objects;
import java.util.UUID;

/**
 * Referencia ligera a una zona de despacho.
 */
public record ZoneReference(UUID zoneId, String code, String name, boolean active) {

    public ZoneReference {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
