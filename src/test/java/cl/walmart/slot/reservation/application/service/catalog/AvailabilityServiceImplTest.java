package cl.walmart.slot.reservation.application.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AvailabilityServiceImplTest {
    private final CatalogZoneId zone = new CatalogZoneId(UUID.randomUUID());
    private final LocalDate first = LocalDate.of(2031, 2, 3);

    @Test
    void loadsOnlyCacheMissesWithOneDatabaseAndHolidayReadAndCachesThem() {
        var cached = day(first, false, 0);
        var cache = new FakeCache(Map.of(first, cached));
        var slots = new FakeSlots(List.of(slot(first.plusDays(1), 1)));
        var holidays = new FakeHolidays(Set.of(first.plusDays(2)));
        var service = new AvailabilityServiceImpl(cache, slots, holidays);

        var actual = service.load(zone, List.of(first, first.plusDays(1), first.plusDays(2)));

        assertThat(actual).extracting(ZoneDayAvailability::date)
                .containsExactly(first, first.plusDays(1), first.plusDays(2));
        assertThat(actual.get(1).closed()).isFalse();
        assertThat(actual.get(1).slots()).hasSize(1);
        assertThat(actual.get(2).closed()).isTrue();
        assertThat(slots.calls).isEqualTo(1);
        assertThat(holidays.calls).isEqualTo(1);
        assertThat(cache.saved).extracting(ZoneDayAvailability::date)
                .containsExactly(first.plusDays(1), first.plusDays(2));
    }

    @Test
    void doesNotReadDatabaseWhenEveryDateIsCached() {
        var cache = new FakeCache(Map.of(first, day(first, false, 0)));
        var slots = new FakeSlots(List.of());
        var holidays = new FakeHolidays(Set.of());

        assertThat(new AvailabilityServiceImpl(cache, slots, holidays).load(zone, List.of(first)))
                .containsExactly(day(first, false, 0));
        assertThat(slots.calls).isZero();
        assertThat(holidays.calls).isZero();
    }

    @Test
    void totalCacheMissIncludesDatesWithoutSlotsAndPersistsEveryRequestedDay() {
        var cache = new FakeCache(Map.of());
        var slots = new FakeSlots(List.of());
        var holidays = new FakeHolidays(Set.of());

        var result = new AvailabilityServiceImpl(cache, slots, holidays).load(zone, List.of(first, first.plusDays(1)));

        assertThat(result).allSatisfy(day -> assertThat(day.slots()).isEmpty());
        assertThat(cache.saved).extracting(ZoneDayAvailability::date).containsExactly(first, first.plusDays(1));
        assertThat(slots.calls).isEqualTo(1);
        assertThat(holidays.calls).isEqualTo(1);
    }

    private ZoneDayAvailability day(LocalDate date, boolean closed, int slotCount) {
        var slots = new ArrayList<DeliverySlot>();
        for (var i = 0; i < slotCount; i++) slots.add(slot(date, i));
        return new ZoneDayAvailability(zone, date, closed, slots);
    }
    private DeliverySlot slot(LocalDate date, int offset) {
        var start = date.atTime(16 + offset, 0).atZone(CatalogTime.BUSINESS_ZONE).toInstant();
        return new DeliverySlot(new DeliverySlotId(UUID.nameUUIDFromBytes((date + ":" + offset).getBytes())), zone, date,
                start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofHours(2)), new Money(2990), 5, 0, 0);
    }
    private final class FakeCache implements AvailabilityCacheRepository {
        final Map<LocalDate, ZoneDayAvailability> values = new HashMap<>(); List<ZoneDayAvailability> saved = List.of();
        FakeCache(Map<LocalDate, ZoneDayAvailability> values) { this.values.putAll(values); }
        public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId z, Collection<LocalDate> dates) {
            return dates.stream().filter(values::containsKey).collect(java.util.stream.Collectors.toMap(d -> d, values::get)); }
        public void saveAll(Collection<ZoneDayAvailability> days) { saved = List.copyOf(days); }
        public void evict(ZoneDate ignored) { }
    }
    private final class FakeSlots implements SlotQueryRepository {
        final List<DeliverySlot> values; int calls; FakeSlots(List<DeliverySlot> values) { this.values = values; }
        public List<DeliverySlot> findByZoneAndDates(CatalogZoneId z, LocalDate from, LocalDate to) { calls++; return values; }
        public Optional<DeliverySlot> findById(DeliverySlotId id) { return Optional.empty(); }
    }
    private final class FakeHolidays implements HolidayRepository {
        final Set<LocalDate> closed; int calls; FakeHolidays(Set<LocalDate> closed) { this.closed = closed; }
        public HolidayCalendar calendarBetween(LocalDate from, LocalDate to) { calls++; return new HolidayCalendar(closed, Map.of()); }
    }
}
