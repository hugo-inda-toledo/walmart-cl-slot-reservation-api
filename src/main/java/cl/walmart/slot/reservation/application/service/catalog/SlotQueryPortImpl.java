package cl.walmart.slot.reservation.application.service.catalog;

import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, noRollbackFor = DomainException.class)
class SlotQueryPortImpl implements SlotQueryPort {
    private final SlotQueryRepository slots; private final HolidayRepository holidays;
    private final PricingPolicy pricing; private final CatalogSettings settings; private final Clock clock;
    SlotQueryPortImpl(SlotQueryRepository slots, HolidayRepository holidays, PricingPolicy pricing,
                      CatalogSettings settings, Clock clock) {
        this.slots = slots; this.holidays = holidays; this.pricing = pricing; this.settings = settings; this.clock = clock;
    }
    @Override public Optional<SlotView> findSlot(UUID slotId) {
        Instant now = clock.instant();
        return slots.findById(new DeliverySlotId(slotId)).map(slot -> toView(slot, now,
                holidays.calendarBetween(slot.deliveryDate(), slot.deliveryDate())));
    }
    @Override public List<SlotView> findAvailableSlots(UUID zoneId, LocalDate from, LocalDate to) {
        validateRange(from, to); Instant now = clock.instant(); var zone = new CatalogZoneId(zoneId);
        var calendar = holidays.calendarBetween(from, to);
        return slots.findByZoneAndDates(zone, from, to).stream().map(slot -> toView(slot, now, calendar))
                .filter(view -> view.status() == SlotView.Status.AVAILABLE)
                .sorted(java.util.Comparator.comparing(SlotView::startAt).thenComparing(SlotView::endAt)).toList();
    }
    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > settings.horizonDays())
            throw new IllegalArgumentException("range must be ordered and within the catalog horizon");
    }
    private SlotView toView(DeliverySlot slot, Instant now, HolidayCalendar calendar) {
        var status = calendar.isClosed(slot.zoneId(), slot.deliveryDate()) ? SlotView.Status.NO_SERVICE
                : SlotView.Status.valueOf(slot.statusAt(now).name());
        return new SlotView(slot.id().value(), slot.zoneId().value(), slot.deliveryDate(), slot.startAt(), slot.endAt(),
                slot.cutoffAt(), pricing.priceFor(slot).amountClp(), slot.capacity(), slot.reserved(), slot.held(),
                slot.remaining(), status);
    }
}
