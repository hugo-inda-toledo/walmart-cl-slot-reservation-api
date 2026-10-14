package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import cl.walmart.slot.reservation.domain.usecase.reservation.PurgeExpiredIdempotencyKeysUseCase;
import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga de claves de idempotencia vencidas (requisito 9.10), con cron configurable en
 * {@code app.reservation.idempotency.purge-cron}. Repite lotes mientras vengan llenos con el mismo tope por
 * ejecución del barredor; una falla se captura y se registra. Deshabilitable con
 * {@code app.reservation.idempotency.purge-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.reservation.idempotency.purge-enabled", havingValue = "true",
        matchIfMissing = true)
public class IdempotencyKeyPurger {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurger.class);

    private final PurgeExpiredIdempotencyKeysUseCase purgeExpiredKeys;
    private final ReservationSettings settings;

    public IdempotencyKeyPurger(PurgeExpiredIdempotencyKeysUseCase purgeExpiredKeys, ReservationSettings settings) {
        this.purgeExpiredKeys = purgeExpiredKeys;
        this.settings = settings;
    }

    /**
     * Ejecuta una pasada de purga.
     *
     * @return cantidad de claves borradas en la pasada
     */
    @Scheduled(cron = "${app.reservation.idempotency.purge-cron}", zone = "America/Santiago")
    public int purge() {
        int purged = 0;
        try {
            for (int batch = 0; batch < settings.sweeperMaxBatchesPerRun(); batch++) {
                int count = purgeExpiredKeys.purgeExpired();
                purged += count;
                if (count < settings.purgeBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Falló la purga programada de claves de idempotencia vencidas; se reintenta en la siguiente "
                    + "ejecución (borradas antes de la falla: {}).", purged, exception);
            return purged;
        }
        if (purged > 0) {
            log.info("Claves de idempotencia vencidas purgadas: {}", purged);
        }
        return purged;
    }
}
