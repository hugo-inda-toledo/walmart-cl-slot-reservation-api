package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.entities.catalog.ResolvedZone;

/** Resuelve la dirección solicitada sin filtrar ni reinterpretar errores de entrada de geo. */
public interface AddressZoneService {
    ResolvedZone resolve(AddressQuery query);
}
