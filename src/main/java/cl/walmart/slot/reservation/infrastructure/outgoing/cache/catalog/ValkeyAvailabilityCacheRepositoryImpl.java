package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
@ConditionalOnProperty(name = "app.catalog.availability-cache.enabled", havingValue = "true", matchIfMissing = true)
class ValkeyAvailabilityCacheRepositoryImpl implements AvailabilityCacheRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ValkeyAvailabilityCacheRepositoryImpl.class);
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Clock clock;
    private final CatalogProperties.AvailabilityCache properties;
    private final MeterRegistry metrics;
    private volatile Instant retryAfter = Instant.MIN;

    ValkeyAvailabilityCacheRepositoryImpl(StringRedisTemplate redis, JsonMapper json, Clock clock, CatalogProperties properties, MeterRegistry metrics) {
        this.redis = redis; this.json = json; this.clock = clock; this.properties = properties.availabilityCache(); this.metrics = metrics;
    }

    @Override public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId zone, Collection<LocalDate> dates) {
        if (isBackedOff()) return Map.of();
        List<LocalDate> requested = List.copyOf(dates);
        try {
            List<String> values = redis.opsForValue().multiGet(requested.stream().map(date -> AvailabilityCacheKeys.forZoneDate(zone, date)).toList());
            Map<LocalDate, ZoneDayAvailability> result = new LinkedHashMap<>();
            for (int index = 0; index < requested.size(); index++) {
                String value = values == null ? null : values.get(index);
                if (value == null) { count("miss"); continue; }
                try {
                    ZoneDayAvailability day = json.readValue(value, CachedZoneDay.class).toDomain();
                    if (!day.zoneId().equals(zone) || !day.date().equals(requested.get(index))) throw new IllegalArgumentException("cache key/value mismatch");
                    result.put(requested.get(index), day); count("hit");
                } catch (RuntimeException parseFailure) {
                    redis.delete(AvailabilityCacheKeys.forZoneDate(zone, requested.get(index))); count("miss");
                    LOG.warn("Valor de caché de disponibilidad ilegible; se tratará como ausencia", parseFailure);
                }
            }
            return Map.copyOf(result);
        } catch (RuntimeException failure) { failed("lectura", failure); return Map.of(); }
    }

    @Override public void saveAll(Collection<ZoneDayAvailability> days) {
        if (days.isEmpty() || isBackedOff()) return;
        try {
            Map<String, String> encoded = new LinkedHashMap<>();
            for (ZoneDayAvailability day : days) encoded.put(AvailabilityCacheKeys.forZoneDate(day.zoneId(), day.date()), json.writeValueAsString(CachedZoneDay.from(day)));
            redis.executePipelined((RedisCallback<Object>) connection -> {
                encoded.forEach((key, value) -> connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8), Expiration.from(properties.ttl()), RedisStringCommands.SetOption.UPSERT));
                return null;
            });
        } catch (RuntimeException failure) { failed("escritura", failure); }
    }

    @Override public void evict(ZoneDate zoneDate) {
        if (isBackedOff()) return;
        try { redis.delete(AvailabilityCacheKeys.forZoneDate(zoneDate.zoneId(), zoneDate.date())); }
        catch (RuntimeException failure) { failed("evicción", failure); }
    }
    private boolean isBackedOff() { if (clock.instant().isBefore(retryAfter)) { count("skipped"); return true; } return false; }
    private void failed(String operation, RuntimeException failure) { retryAfter = clock.instant().plus(properties.failureBackoff()); count("error"); LOG.warn("Fallo de {} de caché de disponibilidad; se aplicará backoff", operation, failure); }
    private void count(String result) { metrics.counter("catalog.availability.cache.requests", "result", result).increment(); }
}
