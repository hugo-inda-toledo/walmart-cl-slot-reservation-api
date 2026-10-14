package cl.walmart.slot.reservation.domain.entities.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CatalogTimeTest {

    @Test
    void businessZoneIsSantiago() {
        assertThat(CatalogTime.BUSINESS_ZONE).isEqualTo(ZoneId.of("America/Santiago"));
    }

    @Test
    void lateEveningInSantiagoIsStillTheSameBusinessDay() {
        // 23:30 del 11-10-2026 en Santiago (UTC-3 en primavera austral) = 02:30 UTC del 12-10
        var clock = Clock.fixed(Instant.parse("2026-10-12T02:30:00Z"), ZoneOffset.UTC);

        assertThat(CatalogTime.today(clock)).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void ignoresTheZoneOfTheInjectedClock() {
        var instant = Instant.parse("2026-10-12T02:30:00Z");
        var tokyo = Clock.fixed(instant, ZoneId.of("Asia/Tokyo"));
        var utc = Clock.fixed(instant, ZoneOffset.UTC);

        assertThat(CatalogTime.today(tokyo)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(CatalogTime.today(utc)).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void firstInstantOfTheDayInSantiagoRollsOver() {
        // 00:00 del 12-10 en Santiago (UTC-3) = 03:00 UTC
        var clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneOffset.UTC);

        assertThat(CatalogTime.today(clock)).isEqualTo(LocalDate.of(2026, 10, 12));
    }
}
