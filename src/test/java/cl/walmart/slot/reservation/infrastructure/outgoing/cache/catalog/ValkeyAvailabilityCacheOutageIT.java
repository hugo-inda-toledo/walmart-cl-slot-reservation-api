package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class ValkeyAvailabilityCacheOutageIT {
    @Test
    void stoppedDedicatedValkeyReturnsMissAndBackoffAvoidsRepeatedConnections() {
        try (GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:9.0.6-alpine")).withExposedPorts(6379)) {
            valkey.start();
            var factory = new LettuceConnectionFactory(valkey.getHost(), valkey.getMappedPort(6379));
            factory.afterPropertiesSet();
            var redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            var metrics = new SimpleMeterRegistry();
            var cache = new ValkeyAvailabilityCacheRepositoryImpl(redis, tools.jackson.databind.json.JsonMapper.builder().build(),
                    Clock.systemUTC(), properties(), metrics);
            valkey.stop();
            var zone = new CatalogZoneId(UUID.fromString("00000000-0000-4000-8000-0000000000ca"));

            assertThat(cache.findAll(zone, List.of(LocalDate.of(2032, 1, 5)))).isEmpty();
            assertThat(cache.findAll(zone, List.of(LocalDate.of(2032, 1, 5)))).isEmpty();
            assertThat(metrics.find("catalog.availability.cache.requests").tag("result", "error").counter().count()).isEqualTo(1);
            assertThat(metrics.find("catalog.availability.cache.requests").tag("result", "skipped").counter().count()).isEqualTo(1);
            factory.destroy();
        }
    }

    private static CatalogProperties properties() {
        return new CatalogProperties(7, new CatalogProperties.Generation(14, "0 15 0 * * *", true), Duration.ofHours(2),
                new CatalogProperties.AvailabilityCache(true, Duration.ofSeconds(30), Duration.ofSeconds(5)),
                new CatalogProperties.Demo(false, UUID.randomUUID(), 2));
    }
}
