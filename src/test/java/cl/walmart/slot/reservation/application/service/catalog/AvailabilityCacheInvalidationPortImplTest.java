package cl.walmart.slot.reservation.application.service.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;

import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AvailabilityCacheInvalidationPortImplTest {
    @Test
    void neverLeaksACacheFailureToReservations() {
        AvailabilityCacheRepository broken = new AvailabilityCacheRepository() {
            public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId z, Collection<LocalDate> d) { return Map.of(); }
            public void saveAll(Collection<ZoneDayAvailability> d) { }
            public void evict(ZoneDate d) { throw new IllegalStateException("Valkey unavailable"); }
        };
        assertThatCode(() -> new AvailabilityCacheInvalidationPortImpl(broken)
                .invalidate(UUID.randomUUID(), LocalDate.of(2031, 2, 3))).doesNotThrowAnyException();
    }
}
