package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;

/**
 * Política base: el costo es el materializado de la plantilla al generar la ventana.
 */
public final class TemplatePricingPolicyImpl implements PricingPolicy {

    @Override
    public Money priceFor(DeliverySlot slot) {
        return slot.basePrice();
    }
}
