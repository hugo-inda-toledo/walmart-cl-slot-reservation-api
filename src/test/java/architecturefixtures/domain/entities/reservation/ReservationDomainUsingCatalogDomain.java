package architecturefixtures.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;

/**
 * Contraejemplo: una clase del dominio de reservation que depende de un tipo del dominio de catalog.
 */
public class ReservationDomainUsingCatalogDomain {

    public DeliverySlotId slotId(DeliverySlotId slotId) {
        return slotId;
    }
}
