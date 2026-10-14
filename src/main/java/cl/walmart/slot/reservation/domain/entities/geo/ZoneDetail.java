package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import java.util.Objects;
import java.util.Optional;

/**
 * Detalle de una zona: el agregado y, si se pidió y existe, su geometría.
 */
public record ZoneDetail(DeliveryZone zone, Optional<ZoneGeometry> geometry) {

    public ZoneDetail {
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(geometry, "geometry must not be null");
    }
}
