package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import cl.walmart.slot.reservation.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class ValkeyAvailabilityCacheRepositoryImplTest {
    @Test
    void treatsAConnectionFailureAsAMissAndSkipsValkeyDuringTheMeasuredBackoff() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(org.mockito.ArgumentMatchers.anyList())).thenThrow(new RedisConnectionFailureException("down"));
        MutableClock clock = new MutableClock(Instant.parse("2032-01-01T00:00:00Z"), ZoneOffset.UTC);
        var cache = new ValkeyAvailabilityCacheRepositoryImpl(redis, JsonMapper.builder().build(), clock, properties(), new SimpleMeterRegistry());
        var zone = new CatalogZoneId(UUID.fromString("00000000-0000-4000-8000-0000000000ca"));

        assertThat(cache.findAll(zone, List.of(LocalDate.of(2032, 1, 5)))).isEmpty();
        assertThat(cache.findAll(zone, List.of(LocalDate.of(2032, 1, 5)))).isEmpty();

        verify(values).multiGet(org.mockito.ArgumentMatchers.anyList());
        verifyNoMoreInteractions(values);
    }

    private static CatalogProperties properties() {
        return new CatalogProperties(7, new CatalogProperties.Generation(14, "0 15 0 * * *", true), Duration.ofHours(2),
                new CatalogProperties.AvailabilityCache(true, Duration.ofSeconds(30), Duration.ofSeconds(5)),
                new CatalogProperties.Demo(false, UUID.randomUUID(), 2));
    }
}
