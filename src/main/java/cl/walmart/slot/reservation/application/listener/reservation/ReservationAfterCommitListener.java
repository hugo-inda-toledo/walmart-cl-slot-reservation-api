package cl.walmart.slot.reservation.application.listener.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.port.catalog.AvailabilityCacheInvalidationPort;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa al catálogo cada zona-fecha cambiada y registra métricas, solo tras un commit real. La caché es un
 * acelerador: una falla del aviso se registra y nunca se propaga ni impide el resto del procesamiento.
 */
@Component
class ReservationAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationAfterCommitListener.class);

    private final AvailabilityCacheInvalidationPort invalidation;
    private final ReservationMetrics metrics;

    ReservationAfterCommitListener(AvailabilityCacheInvalidationPort invalidation, ReservationMetrics metrics) {
        this.invalidation = invalidation;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ReservationLifecycleEvent event) {
        for (var zoneDay : event.zoneDays()) {
            try {
                invalidation.invalidate(zoneDay.zoneId(), zoneDay.date());
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar al catálogo el cambio de {} {}", zoneDay.zoneId(), zoneDay.date(), e);
            }
        }
        try {
            metrics.lifecycle(event.kind(), event.count());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la métrica de ciclo de vida {}", event.kind(), e);
        }
    }
}
