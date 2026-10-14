package cl.walmart.slot.reservation.integration.catalog.seam;

import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj controlable en 2037, compartido por los tests del seam con reservas y la generación concurrente. */
@TestConfiguration(proxyBeanMethods = false)
class SeamClockConfiguration {

    static final Instant TODAY_MORNING = Instant.parse("2037-03-05T13:00:00Z"); // 10:00 Santiago (UTC-3)

    @Bean
    @Primary
    MutableClock seamClock() {
        return new MutableClock(TODAY_MORNING, ZoneId.of("America/Santiago"));
    }
}
