package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.usecase.catalog.SeedSoldOutDemoDayUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SeedSoldOutDemoDayUseCaseImpl implements SeedSoldOutDemoDayUseCase {
    private final SlotQueryRepository slots; private final HolidayRepository holidays;
    private final SlotCalendarRepository calendar; private final CatalogSettings settings;
    private final Clock clock; private final ApplicationEventPublisher events;
    SeedSoldOutDemoDayUseCaseImpl(SlotQueryRepository slots, HolidayRepository holidays,
                                  SlotCalendarRepository calendar, CatalogSettings settings, Clock clock,
                                  ApplicationEventPublisher events) {
        this.slots = slots; this.holidays = holidays; this.calendar = calendar; this.settings = settings;
        this.clock = clock; this.events = events;
    }
    @Override @Transactional
    public Optional<LocalDate> seed(CatalogZoneId zone, int offsetDays) {
        if (offsetDays < 0) throw new IllegalArgumentException("offsetDays must not be negative");
        var today = CatalogTime.today(clock); var from = today.plusDays(offsetDays);
        var to = today.plusDays(settings.horizonDays() - 1L);
        if (from.isAfter(to)) return Optional.empty();
        Instant now = clock.instant(); var closed = holidays.calendarBetween(from, to);
        var byDate = slots.findByZoneAndDates(zone, from, to).stream()
                .collect(java.util.stream.Collectors.groupingBy(DeliverySlot::deliveryDate));
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (closed.isClosed(zone, date)) continue;
            var open = byDate.getOrDefault(date, java.util.List.of()).stream()
                    .filter(slot -> slot.cutoffAt().isAfter(now)).toList();
            if (open.isEmpty()) continue;
            // Idempotente entre ejecuciones diarias: si el día ya está agotado, se reutiliza en vez de cerrar otro.
            if (open.stream().allMatch(slot -> slot.remaining() == 0)) return Optional.of(date);
            if (calendar.closeRemainingCapacity(zone, date, now) > 0) {
                events.publishEvent(new ZoneDaysChangedEvent(java.util.Set.of(new ZoneDate(zone, date))));
            }
            return Optional.of(date);
        }
        return Optional.empty();
    }
}
