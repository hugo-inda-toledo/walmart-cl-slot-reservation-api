package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GenerateSlotsUseCaseImpl implements GenerateSlotsUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(GenerateSlotsUseCaseImpl.class);
    private final SlotTemplateRepository templates; private final HolidayRepository holidays;
    private final SlotCalendarRepository calendar; private final ZoneResolutionPort zones;
    private final CutoffPolicy cutoff; private final CatalogSettings settings; private final Clock clock;
    private final ApplicationEventPublisher events;
    GenerateSlotsUseCaseImpl(SlotTemplateRepository templates, HolidayRepository holidays, SlotCalendarRepository calendar,
                             ZoneResolutionPort zones, CutoffPolicy cutoff, CatalogSettings settings, Clock clock,
                             ApplicationEventPublisher events) {
        this.templates = templates; this.holidays = holidays; this.calendar = calendar; this.zones = zones;
        this.cutoff = cutoff; this.settings = settings; this.clock = clock; this.events = events;
    }
    @Override @Transactional
    public GenerationReport generate() {
        var from = CatalogTime.today(clock); var to = from.plusDays(settings.horizonDays() - 1L);
        var holidayCalendar = holidays.calendarBetween(from, to);
        var slots = new ArrayList<NewDeliverySlot>();
        var activeByZone = new HashMap<CatalogZoneId, Boolean>();
        for (var template : templates.findAllActive()) {
            var active = activeByZone.computeIfAbsent(template.zoneId(), id ->
                    zones.findZone(id.value()).map(ref -> ref.active()).orElse(false));
            if (!active) continue;
            for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (!template.appliesTo(date) || holidayCalendar.isClosed(template.zoneId(), date)) continue;
                try { slots.add(template.instantiate(date, cutoff)); }
                catch (IllegalArgumentException error) { LOG.warn("Se omite plantilla {} para fecha {}", template.id().value(), date, error); }
            }
        }
        var insertion = slots.isEmpty() ? SlotCalendarInsertion.empty() : calendar.insertMissing(slots);
        var touched = insertion.touched();
        if (!touched.isEmpty()) events.publishEvent(new ZoneDaysChangedEvent(touched));
        return new GenerationReport(from, to, insertion.created(), touched);
    }
}
