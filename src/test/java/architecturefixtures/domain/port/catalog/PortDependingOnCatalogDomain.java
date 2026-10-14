package architecturefixtures.domain.port.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;

/**
 * Contraejemplo: una clase del paquete del puerto publicado de catalog que expone un tipo del dominio de catalog.
 */
public interface PortDependingOnCatalogDomain {

    DeliverySlotId slotId();
}
