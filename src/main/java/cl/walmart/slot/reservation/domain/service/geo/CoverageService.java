package cl.walmart.slot.reservation.domain.service.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;

/**
 * Única implementación de las reglas de cobertura; la reutilizan el endpoint HTTP y el puerto publicado.
 */
public interface CoverageService {

    /**
     * Resuelve comuna y zona de la dirección. La calle y el número no intervienen.
     *
     * @throws cl.walmart.slot.reservation.domain.exception.geo.GeoException {@code UNKNOWN_COMUNA} si la comuna declarada
     *                                                            no existe
     */
    CoverageResult resolve(Address address);
}
