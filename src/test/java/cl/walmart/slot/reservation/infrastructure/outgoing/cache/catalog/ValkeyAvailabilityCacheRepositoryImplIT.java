package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@IntegrationTest
class ValkeyAvailabilityCacheRepositoryImplIT {

    private static final CatalogZoneId ZONE = new CatalogZoneId(UUID.fromString("00000000-0000-4000-8000-0000000000ca"));
    private static final LocalDate DATE = LocalDate.of(2032, 1, 5);

    @Autowired private AvailabilityCacheRepository cache;
    @Autowired private StringRedisTemplate redis;

    @Test
    void roundTripsRawAvailabilityWithTtlAndEviction() {
        ZoneDayAvailability day = new ZoneDayAvailability(ZONE, DATE, false, List.of(new DeliverySlot(
                new DeliverySlotId(UUID.fromString("00000000-0000-4000-8004-0000000000ca")), ZONE, DATE,
                Instant.parse("2032-01-05T19:00:00Z"), Instant.parse("2032-01-05T21:00:00Z"),
                Instant.parse("2032-01-05T17:00:00Z"), new Money(2990), 8, 2, 1)));

        cache.saveAll(List.of(day));

        assertThat(cache.findAll(ZONE, List.of(DATE)).get(DATE)).usingRecursiveComparison().isEqualTo(day);
        assertThat(redis.getExpire(AvailabilityCacheKeys.forZoneDate(ZONE, DATE))).isBetween(1L, 30L);
        cache.evict(new ZoneDate(ZONE, DATE));
        assertThat(cache.findAll(ZONE, List.of(DATE))).isEmpty();
    }

    @Test
    void treatsAnUnreadableValueAsAMissAndDeletesIt() {
        redis.opsForValue().set(AvailabilityCacheKeys.forZoneDate(ZONE, DATE), "not-json");

        assertThat(cache.findAll(ZONE, List.of(DATE))).isEmpty();
        assertThat(redis.hasKey(AvailabilityCacheKeys.forZoneDate(ZONE, DATE))).isFalse();
    }
}
