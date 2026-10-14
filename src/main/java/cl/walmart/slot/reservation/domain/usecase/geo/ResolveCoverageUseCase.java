package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;

/**
 * Resuelve la cobertura de una dirección para el cliente: una dirección sin cobertura es un error de negocio.
 */
public interface ResolveCoverageUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.geo.AddressNotCoveredException si la dirección no tiene cobertura
     * @throws cl.walmart.slot.reservation.domain.exception.geo.GeoException               {@code UNKNOWN_COMUNA}
     */
    CoverageResult.Covered resolve(Address address);
}
