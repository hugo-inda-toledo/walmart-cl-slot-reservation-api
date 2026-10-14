package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.List;

public record DeliverySlotsView(ResolvedZone zone, DeliveryDateView date, List<PricedSlotView> slots) {
    public DeliverySlotsView { slots = List.copyOf(slots); }
}
