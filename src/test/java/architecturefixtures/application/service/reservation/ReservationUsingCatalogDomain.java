package architecturefixtures.application.service.reservation;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;

/**
 * Contraejemplo: un consumidor de reservas que usa un tipo del dominio de catalog en vez del puerto publicado.
 */
public class ReservationUsingCatalogDomain {

    public DeliverySlotId slotId(DeliverySlotId slotId) {
        return slotId;
    }
}
