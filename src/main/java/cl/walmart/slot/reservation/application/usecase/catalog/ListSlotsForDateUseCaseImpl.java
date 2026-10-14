package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.service.catalog.AddressZoneService;
import cl.walmart.slot.reservation.domain.service.catalog.AvailabilityService;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotsView;
import cl.walmart.slot.reservation.domain.usecase.catalog.ListSlotsForDateUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.PricedSlotView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateLabel;
import cl.walmart.slot.reservation.domain.entities.catalog.DisplayPeriod;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogTime;
import cl.walmart.slot.reservation.domain.service.catalog.PricingPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListSlotsForDateUseCaseImpl implements ListSlotsForDateUseCase {
    private final AddressZoneService addressZoneService; private final AvailabilityService availabilityService;
    private final CatalogSettings settings; private final PricingPolicy pricing; private final Clock clock;
    ListSlotsForDateUseCaseImpl(AddressZoneService addressZoneService, AvailabilityService availabilityService,
                                CatalogSettings settings, PricingPolicy pricing, Clock clock) {
        this.addressZoneService = addressZoneService; this.availabilityService = availabilityService;
        this.settings = settings; this.pricing = pricing; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public DeliverySlotsView list(AddressQuery query, LocalDate date) {
        Instant now = clock.instant();
        var period = new DisplayPeriod(java.time.LocalDate.ofInstant(now, CatalogTime.BUSINESS_ZONE), settings.displayDays());
        period.requireContains(date);
        var zone = addressZoneService.resolve(query);
        var day = availabilityService.load(zone.zoneId(), List.of(date)).getFirst();
        var dateView = new DeliveryDateView(date, DeliveryDateLabel.of(date, period.today()), day.dateStatusAt(now));
        var slots = day.visibleSlotsAt(now).stream().map(slot -> new PricedSlotView(slot.id(), slot.startAt(),
                slot.endAt(), pricing.priceFor(slot), slot.remaining(), slot.statusAt(now))).toList();
        return new DeliverySlotsView(zone, dateView, slots);
    }
}
