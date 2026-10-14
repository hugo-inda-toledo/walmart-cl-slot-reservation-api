package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cierres de despacho: feriados nacionales (todas las zonas) y bloqueos por zona.
 */
public final class HolidayCalendar {

    private final Set<LocalDate> national;
    private final Map<CatalogZoneId, Set<LocalDate>> zoneBlocks;

    public HolidayCalendar(Set<LocalDate> national, Map<CatalogZoneId, Set<LocalDate>> zoneBlocks) {
        this.national = Set.copyOf(Objects.requireNonNull(national, "national must not be null"));
        Objects.requireNonNull(zoneBlocks, "zoneBlocks must not be null");
        this.zoneBlocks = zoneBlocks.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(Objects.requireNonNull(entry.getValue(), "zone block dates must not be null"))));
    }

    public boolean isClosed(CatalogZoneId zone, LocalDate date) {
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(date, "date must not be null");
        return national.contains(date) || zoneBlocks.getOrDefault(zone, Set.of()).contains(date);
    }
}
