package cl.walmart.slot.reservation.integration.catalog.acceleration;

import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj controlable en 2036 (lejos de las ventanas generadas con el reloj real y de otros tests). */
@TestConfiguration(proxyBeanMethods = false)
class AccelerationClockConfiguration {

    static final Instant TODAY_MORNING = Instant.parse("2036-03-05T13:00:00Z"); // 10:00 Santiago (UTC-3)

    @Bean
    @Primary
    MutableClock accelerationClock() {
        return new MutableClock(TODAY_MORNING, ZoneId.of("America/Santiago"));
    }
}
