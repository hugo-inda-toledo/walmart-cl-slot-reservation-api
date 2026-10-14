package cl.walmart.slot.reservation.integration.catalog.acceleration;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Caché activa contra Valkey y PostgreSQL reales: la respuesta se sirve de la caché hasta que se avisa el cambio
 * por el puerto de invalidación, y el reloj sigue gobernando el estado aunque la respuesta venga de la caché.
 */
@IntegrationTest
@Import(AccelerationClockConfiguration.class)
class AvailabilityAccelerationActiveIT extends AvailabilityAccelerationScenario {

    @Autowired
    private MeterRegistry metrics;

    @Test
    void producesTheExpectedResponses() throws Exception {
        assertScenarioMatchesTheExpectedObservations();
    }

    @Test
    void keepsServingTheCachedDayUntilTheChangeIsNotifiedThroughThePort() throws Exception {
        LocalDate soldOutDay = LocalDate.of(2036, 3, 7);
        assertThat(slots(soldOutDay)).startsWith("AVAILABLE");   // llena la caché de la zona-fecha

        fillSoldOutDayCountersBySql();
        assertThat(slots(soldOutDay)).as("sin aviso, la caché sigue sirviendo el valor anterior")
                .startsWith("AVAILABLE");

        notifySoldOutDayChange();
        assertThat(slots(soldOutDay)).as("tras el aviso, la consulta siguiente refleja los contadores")
                .startsWith("SOLD_OUT");
    }

    @Test
    void advancesTheStatusWithTheClockEvenWhenTheResponseComesFromTheCache() throws Exception {
        LocalDate today = LocalDate.of(2036, 3, 5);
        assertThat(slots(today)).isEqualTo("AVAILABLE [AVAILABLE/8/2990, AVAILABLE/8/2990, AVAILABLE/8/2990, AVAILABLE/8/2990]");
        double hitsBefore = hits();

        clock.setInstant(Instant.parse("2036-03-05T17:30:00Z")); // 14:30 Santiago

        assertThat(slots(today)).startsWith("AVAILABLE [CUTOFF_PASSED/8/2990");
        assertThat(hits()).as("la segunda lectura salió de la caché").isGreaterThan(hitsBefore);
    }

    private double hits() {
        var counter = metrics.find("catalog.availability.cache.requests").tag("result", "hit").counter();
        return counter == null ? 0 : counter.count();
    }
}
