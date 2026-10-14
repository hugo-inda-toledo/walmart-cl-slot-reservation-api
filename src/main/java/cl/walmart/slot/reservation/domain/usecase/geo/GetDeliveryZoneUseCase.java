package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ZoneDetail;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;

/**
 * Obtiene el detalle de una zona de despacho, opcionalmente con su geometría.
 */
public interface GetDeliveryZoneUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.geo.GeoException {@code ZONE_NOT_FOUND} si la zona no existe
     */
    ZoneDetail get(DeliveryZoneId id, boolean includeGeometry);
}
