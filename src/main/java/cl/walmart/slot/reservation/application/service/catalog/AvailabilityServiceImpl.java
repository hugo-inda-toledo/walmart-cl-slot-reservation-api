package cl.walmart.slot.reservation.application.service.catalog;

import cl.walmart.slot.reservation.domain.service.catalog.AvailabilityService;
import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.repository.catalog.HolidayRepository;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotQueryRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
class AvailabilityServiceImpl implements AvailabilityService {
    private final AvailabilityCacheRepository cache;
    private final SlotQueryRepository slots;
    private final HolidayRepository holidays;

    AvailabilityServiceImpl(AvailabilityCacheRepository cache, SlotQueryRepository slots, HolidayRepository holidays) {
        this.cache = cache;
        this.slots = slots;
        this.holidays = holidays;
    }

    @Override
    public List<ZoneDayAvailability> load(CatalogZoneId zone, List<LocalDate> dates) {
        Objects.requireNonNull(zone, "zone must not be null");
        var requested = List.copyOf(Objects.requireNonNull(dates, "dates must not be null"));
        if (requested.isEmpty()) return List.of();
        var result = new HashMap<>(cache.findAll(zone, requested));
        var missing = requested.stream().filter(date -> !result.containsKey(date)).distinct().toList();
        if (!missing.isEmpty()) {
            var from = missing.stream().min(Comparator.naturalOrder()).orElseThrow();
            var to = missing.stream().max(Comparator.naturalOrder()).orElseThrow();
            var calendar = holidays.calendarBetween(from, to);
            Map<LocalDate, List<cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot>> byDate = new HashMap<>();
            for (var slot : slots.findByZoneAndDates(zone, from, to)) {
                byDate.computeIfAbsent(slot.deliveryDate(), ignored -> new ArrayList<>()).add(slot);
            }
            var loaded = missing.stream().map(date -> new ZoneDayAvailability(zone, date,
                    calendar.isClosed(zone, date), byDate.getOrDefault(date, List.of()))).toList();
            loaded.forEach(day -> result.put(day.date(), day));
            cache.saveAll(loaded);
        }
        return requested.stream().map(result::get).toList();
    }
}
