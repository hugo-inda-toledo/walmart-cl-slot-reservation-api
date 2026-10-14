package cl.walmart.slot.reservation.application.listener.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDaysChangedEvent;
import cl.walmart.slot.reservation.domain.repository.catalog.AvailabilityCacheRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Mantiene la caché como acelerador: la invalida solamente después de un commit real. */
@Component
class AvailabilityCacheEvictionListener {
    private final AvailabilityCacheRepository cache;
    AvailabilityCacheEvictionListener(AvailabilityCacheRepository cache) { this.cache = cache; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ZoneDaysChangedEvent event) { event.zoneDates().forEach(cache::evict); }
}
