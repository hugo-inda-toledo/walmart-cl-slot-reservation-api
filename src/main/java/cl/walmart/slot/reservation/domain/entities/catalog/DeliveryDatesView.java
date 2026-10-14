package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.List;

public record DeliveryDatesView(ResolvedZone zone, List<DeliveryDateView> dates) {
    public DeliveryDatesView { dates = List.copyOf(dates); }
}
