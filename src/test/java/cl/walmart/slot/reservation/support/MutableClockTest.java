package cl.walmart.slot.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    void canBeSetAndAdvancedForDeterministicTests() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("America/Santiago"));

        clock.setInstant(Instant.parse("2026-01-02T00:00:00Z"));
        clock.advance(Duration.ofHours(3));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-01-02T03:00:00Z"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Santiago"));
    }
}
