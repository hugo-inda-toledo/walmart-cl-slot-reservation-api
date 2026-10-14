package cl.walmart.slot.reservation.domain.repository.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import java.util.List;

/**
 * Puerto de lectura de regiones.
 */
public interface RegionRepository {

    /** Todas las regiones ordenadas de norte a sur. */
    List<Region> findAllOrderedNorthToSouth();

    boolean exists(RegionCode code);
}
