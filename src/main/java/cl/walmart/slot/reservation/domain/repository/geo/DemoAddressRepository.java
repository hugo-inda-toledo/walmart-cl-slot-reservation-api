package cl.walmart.slot.reservation.domain.repository.geo;

import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import java.util.List;

/**
 * Puerto de lectura de direcciones de demostración.
 */
public interface DemoAddressRepository {

    List<DemoAddress> findAllOrdered();
}
