package cl.walmart.slot.reservation.application.service.catalog;

import cl.walmart.slot.reservation.domain.port.catalog.AvailabilityCacheInvalidationPort;
import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AvailabilityCacheInvalidationPortImpl implements AvailabilityCacheInvalidationPort {
    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityCacheInvalidationPortImpl.class);
    private final AvailabilityCacheRepository cache;
    AvailabilityCacheInvalidationPortImpl(AvailabilityCacheRepository cache) { this.cache = cache; }
    @Override public void invalidate(UUID zoneId, LocalDate deliveryDate) {
        try { cache.evict(new ZoneDate(new CatalogZoneId(zoneId), deliveryDate)); }
        catch (RuntimeException error) { LOG.warn("No se pudo invalidar caché de zona-fecha", error); }
    }
}
