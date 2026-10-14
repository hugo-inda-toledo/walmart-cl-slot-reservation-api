package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.service.catalog.AddressZoneService;
import cl.walmart.slot.reservation.domain.service.catalog.AvailabilityService;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDatesView;
import cl.walmart.slot.reservation.domain.usecase.catalog.ListAvailableDatesUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateLabel;
import cl.walmart.slot.reservation.domain.entities.catalog.DisplayPeriod;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogTime;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListAvailableDatesUseCaseImpl implements ListAvailableDatesUseCase {
    private final AddressZoneService addressZoneService; private final AvailabilityService availabilityService;
    private final CatalogSettings settings; private final Clock clock;
    ListAvailableDatesUseCaseImpl(AddressZoneService addressZoneService, AvailabilityService availabilityService,
                                  CatalogSettings settings, Clock clock) {
        this.addressZoneService = addressZoneService; this.availabilityService = availabilityService;
        this.settings = settings; this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public DeliveryDatesView list(AddressQuery query) {
        Instant now = clock.instant();
        var period = new DisplayPeriod(java.time.LocalDate.ofInstant(now, CatalogTime.BUSINESS_ZONE), settings.displayDays());
        var zone = addressZoneService.resolve(query);
        var dates = availabilityService.load(zone.zoneId(), period.dates()).stream()
                .map(day -> new DeliveryDateView(day.date(), DeliveryDateLabel.of(day.date(), period.today()),
                        day.dateStatusAt(now))).toList();
        return new DeliveryDatesView(zone, dates);
    }
}
