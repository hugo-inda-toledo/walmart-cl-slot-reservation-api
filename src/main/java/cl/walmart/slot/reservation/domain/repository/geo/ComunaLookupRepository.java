package cl.walmart.slot.reservation.domain.repository.geo;

import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de comunas y búsquedas espaciales sobre sus límites.
 */
public interface ComunaLookupRepository {

    Optional<Comuna> findByCode(ComunaCode code);

    /** Comunas de una región, sin orden garantizado. */
    List<Comuna> findByRegion(RegionCode code);

    /** Comuna cuyo límite cubre el punto (borde incluido); desempate por menor CUT. */
    Optional<Comuna> findCovering(GeoPoint point);

    /** Comuna más cercana dentro de la tolerancia; desempate por distancia y luego CUT. */
    Optional<Comuna> findNearestWithin(GeoPoint point, BorderTolerance tolerance);
}
