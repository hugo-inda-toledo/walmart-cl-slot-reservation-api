package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityCacheKeysTest {

    @Test
    void createsThePublishedVersionedAvailabilityKey() {
        assertThat(AvailabilityCacheKeys.forZoneDate(
                new CatalogZoneId(UUID.fromString("5F1C0E2A-0000-4000-8000-000000000401")), LocalDate.of(2032, 1, 5)))
                .isEqualTo("catalog:availability:v1:5f1c0e2a-0000-4000-8000-000000000401:2032-01-05");
    }
}
