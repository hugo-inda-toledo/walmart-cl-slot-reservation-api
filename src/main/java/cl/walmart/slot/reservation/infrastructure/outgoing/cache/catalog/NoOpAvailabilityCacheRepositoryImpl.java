package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.catalog.availability-cache.enabled", havingValue = "false")
class NoOpAvailabilityCacheRepositoryImpl implements AvailabilityCacheRepository {
    @Override public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId zone, Collection<LocalDate> dates) { return Map.of(); }
    @Override public void saveAll(Collection<ZoneDayAvailability> days) { }
    @Override public void evict(ZoneDate zoneDate) { }
}
