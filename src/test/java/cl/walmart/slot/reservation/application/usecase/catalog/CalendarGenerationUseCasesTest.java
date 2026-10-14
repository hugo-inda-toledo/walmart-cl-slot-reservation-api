package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import cl.walmart.slot.reservation.domain.port.geo.*;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CalendarGenerationUseCasesTest {
    private final CatalogZoneId zone = new CatalogZoneId(UUID.randomUUID());
    private final Instant now = Instant.parse("2031-02-03T12:00:00Z");

    @Test
    void generatesOnlyForActiveZonesAndOpenDatesAndPublishesOnlyWhenCreated() {
        var template = template(DayOfWeek.MONDAY);
        var secondTemplate = template(DayOfWeek.MONDAY);
        var inserted = new ArrayList<NewDeliverySlot>(); var events = new ArrayList<Object>();
        SlotCalendarRepository calendar = calendar(inserted, 1);
        var lookups = new int[1];
        ZoneResolutionPort zones = zonePort(true, lookups);
        var useCase = new GenerateSlotsUseCaseImpl(() -> List.of(template, secondTemplate),
                (from, to) -> new HolidayCalendar(Set.of(), Map.of()), calendar, zones,
                new FixedLeadTimeCutoffPolicy(Duration.ofHours(2)), new CatalogSettings(2, 2),
                new MutableClock(now, ZoneOffset.UTC), events::add);

        var report = useCase.generate();

        assertThat(inserted).hasSize(2);
        assertThat(report.created()).isEqualTo(2);
        assertThat(lookups[0]).isEqualTo(1);
        assertThat(events).singleElement().isInstanceOf(ZoneDaysChangedEvent.class);
    }

    @Test
    void demoSeedSkipsClosedDatesAndDoesNotPublishWhenClosingIsAlreadyIdempotent() {
        var date = LocalDate.of(2031, 2, 3); var events = new ArrayList<Object>();
        var slot = slot(date);
        SlotQueryRepository slots = new SlotQueryRepository() {
            public List<DeliverySlot> findByZoneAndDates(CatalogZoneId z, LocalDate f, LocalDate t) { return List.of(slot); }
            public Optional<DeliverySlot> findById(DeliverySlotId id) { return Optional.empty(); }
        };
        var useCase = new SeedSoldOutDemoDayUseCaseImpl(slots,
                (from, to) -> new HolidayCalendar(Set.of(date), Map.of()), calendar(List.of(), 0),
                new CatalogSettings(2, 2), new MutableClock(now, ZoneOffset.UTC), events::add);

        assertThat(useCase.seed(zone, 0)).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void generationOmitsInactiveAndMissingZonesAndNationalHolidays() {
        var inserted = new ArrayList<NewDeliverySlot>();
        var active = template(DayOfWeek.MONDAY);
        ZoneResolutionPort missing = new ZoneResolutionPort() {
            public ZoneResolution resolveZone(ZoneResolutionQuery query) { throw new UnsupportedOperationException(); }
            public Optional<ZoneReference> findZone(UUID id) { return Optional.empty(); }
        };
        var useCase = new GenerateSlotsUseCaseImpl(() -> List.of(active),
                (from, to) -> new HolidayCalendar(Set.of(LocalDate.of(2031, 2, 3)), Map.of()), calendar(inserted, 0),
                missing, new FixedLeadTimeCutoffPolicy(Duration.ofHours(2)), new CatalogSettings(2, 2),
                new MutableClock(now, ZoneOffset.UTC), event -> { });
        assertThat(useCase.generate().created()).isZero();
        assertThat(inserted).isEmpty();
    }

    @Test
    void demoSeedSelectsSameEligibleDateAgainButPublishesOnlyWhenRowsChange() {
        var date = LocalDate.of(2031, 2, 3); var events = new ArrayList<Object>();
        SlotQueryRepository slots = new SlotQueryRepository() {
            public List<DeliverySlot> findByZoneAndDates(CatalogZoneId z, LocalDate f, LocalDate t) { return List.of(slot(date)); }
            public Optional<DeliverySlot> findById(DeliverySlotId id) { return Optional.empty(); }
        };
        var closes = new int[] {1};
        SlotCalendarRepository calendar = new SlotCalendarRepository() {
            public SlotCalendarInsertion insertMissing(List<NewDeliverySlot> values) { return SlotCalendarInsertion.empty(); }
            public int closeRemainingCapacity(CatalogZoneId z, LocalDate d, Instant n) { return closes[0]-- > 0 ? 1 : 0; }
        };
        var useCase = new SeedSoldOutDemoDayUseCaseImpl(slots, (f, t) -> new HolidayCalendar(Set.of(), Map.of()), calendar,
                new CatalogSettings(2, 2), new MutableClock(now, ZoneOffset.UTC), events::add);
        assertThat(useCase.seed(zone, 0)).contains(date);
        assertThat(useCase.seed(zone, 0)).contains(date);
        assertThat(events).hasSize(1);
    }

    private SlotTemplate template(DayOfWeek day) {
        return new SlotTemplate(new SlotTemplateId(UUID.randomUUID()), zone, day,
                new TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0)), new Capacity(5), new Money(2990),
                ServiceType.HOME_DELIVERY, true);
    }
    private DeliverySlot slot(LocalDate date) {
        var start = date.atTime(18, 0).atZone(CatalogTime.BUSINESS_ZONE).toInstant();
        return new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), zone, date, start, start.plus(Duration.ofHours(2)),
                start.minus(Duration.ofHours(2)), new Money(2990), 5, 0, 0);
    }
    private SlotCalendarRepository calendar(List<NewDeliverySlot> inserted, int closes) {
        return new SlotCalendarRepository() {
            public SlotCalendarInsertion insertMissing(List<NewDeliverySlot> slots) {
                inserted.addAll(slots); return slots.isEmpty() ? SlotCalendarInsertion.empty()
                        : new SlotCalendarInsertion(slots.size(), Set.of(new ZoneDate(zone, slots.getFirst().deliveryDate()))); }
            public int closeRemainingCapacity(CatalogZoneId z, LocalDate d, Instant n) { return closes; }
        };
    }
    private ZoneResolutionPort zonePort(boolean active, int[] lookups) {
        return new ZoneResolutionPort() {
            public ZoneResolution resolveZone(ZoneResolutionQuery q) { throw new UnsupportedOperationException(); }
            public Optional<ZoneReference> findZone(UUID id) { lookups[0]++; return Optional.of(new ZoneReference(id, "Z", "Zona", active)); }
        };
    }
}
