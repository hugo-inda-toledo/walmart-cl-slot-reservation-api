package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Region;
import java.util.List;

/**
 * Lista las regiones de Chile ordenadas de norte a sur.
 */
public interface ListRegionsUseCase {

    List<Region> list();
}
