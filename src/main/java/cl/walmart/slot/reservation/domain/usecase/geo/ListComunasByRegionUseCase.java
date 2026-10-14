package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import java.util.List;

/**
 * Lista las comunas de una región en orden alfabético español.
 */
public interface ListComunasByRegionUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.geo.GeoException {@code REGION_NOT_FOUND} si la región no existe
     */
    List<Comuna> list(RegionCode regionCode);
}
