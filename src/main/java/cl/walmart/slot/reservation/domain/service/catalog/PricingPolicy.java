package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;

/**
 * Política de precio de una ventana (punto de extensión: una regla nueva envuelve a la base). Debe ser pura.
 */
public interface PricingPolicy {

    Money priceFor(DeliverySlot slot);
}
