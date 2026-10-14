package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HolidayCalendarTest {

    private static final CatalogZoneId ZONE_A = new CatalogZoneId(UUID.randomUUID());
    private static final CatalogZoneId ZONE_B = new CatalogZoneId(UUID.randomUUID());
    private static final LocalDate NATIONAL = LocalDate.of(2026, 12, 25);
    private static final LocalDate BLOCK = LocalDate.of(2026, 10, 20);

    private final HolidayCalendar calendar =
            new HolidayCalendar(Set.of(NATIONAL), Map.of(ZONE_A, Set.of(BLOCK)));

    @Test
    void nationalHolidayClosesEveryZone() {
        assertThat(calendar.isClosed(ZONE_A, NATIONAL)).isTrue();
        assertThat(calendar.isClosed(ZONE_B, NATIONAL)).isTrue();
    }

    @Test
    void zoneBlockClosesOnlyThatZone() {
        assertThat(calendar.isClosed(ZONE_A, BLOCK)).isTrue();
        assertThat(calendar.isClosed(ZONE_B, BLOCK)).isFalse();
    }

    @Test
    void ordinaryDateIsOpen() {
        assertThat(calendar.isClosed(ZONE_A, LocalDate.of(2026, 10, 14))).isFalse();
    }

    @Test
    void copiesZoneBlockSetsDefensively() {
        var blocks = new HashSet<>(Set.of(BLOCK));
        var source = new HashMap<CatalogZoneId, Set<LocalDate>>();
        source.put(ZONE_A, blocks);
        var calendar = new HolidayCalendar(Set.of(), source);

        blocks.add(LocalDate.of(2026, 10, 21));

        assertThat(calendar.isClosed(ZONE_A, LocalDate.of(2026, 10, 21))).isFalse();
    }
}
