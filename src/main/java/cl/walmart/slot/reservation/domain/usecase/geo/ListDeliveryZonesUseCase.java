package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;

/**
 * Lista las zonas de despacho ordenadas por código, con filtro opcional por estado.
 */
public interface ListDeliveryZonesUseCase {

    List<DeliveryZone> list(Optional<ZoneStatus> status);
}
