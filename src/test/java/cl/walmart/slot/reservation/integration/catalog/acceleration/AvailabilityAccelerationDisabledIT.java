package cl.walmart.slot.reservation.integration.catalog.acceleration;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Caché deshabilitada por configuración: las mismas respuestas que con la caché activa. */
@IntegrationTest
@Import(AccelerationClockConfiguration.class)
@TestPropertySource(properties = "app.catalog.availability-cache.enabled=false")
class AvailabilityAccelerationDisabledIT extends AvailabilityAccelerationScenario {

    @Test
    void producesTheSameResponsesAsWithTheCacheEnabled() throws Exception {
        assertScenarioMatchesTheExpectedObservations();
    }
}
