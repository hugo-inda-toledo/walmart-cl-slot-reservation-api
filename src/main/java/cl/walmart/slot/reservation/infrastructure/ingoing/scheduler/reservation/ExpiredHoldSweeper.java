package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barredor de retenciones vencidas (requisitos 7.1, 7.3, 7.6, 7.7, 7.8). Cada ejecución repite el caso de uso, una
 * transacción por lote, mientras el lote venga lleno y hasta {@code max-batches-per-run}. Una falla se captura y
 * se registra: no llega a las peticiones y la siguiente ejecución reintenta; lo liberado en lotes previos queda
 * confirmado. Deshabilitable con {@code app.reservation.sweeper.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.reservation.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredHoldSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredHoldSweeper.class);

    private final ReleaseExpiredHoldsUseCase releaseExpiredHolds;
    private final ReservationSettings settings;

    public ExpiredHoldSweeper(ReleaseExpiredHoldsUseCase releaseExpiredHolds, ReservationSettings settings) {
        this.releaseExpiredHolds = releaseExpiredHolds;
        this.settings = settings;
    }

    /**
     * Ejecuta una pasada del barredor.
     *
     * @return cantidad de retenciones liberadas en la pasada (las de lotes previos a una falla incluidas)
     */
    @Scheduled(fixedDelayString = "${app.reservation.sweeper.fixed-delay}")
    public int sweep() {
        int released = 0;
        try {
            for (int batch = 0; batch < settings.sweeperMaxBatchesPerRun(); batch++) {
                int count = releaseExpiredHolds.releaseExpired();
                released += count;
                if (count < settings.sweeperBatchSize()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Falló la liberación programada de retenciones vencidas; se reintenta en la siguiente "
                    + "ejecución (liberadas antes de la falla: {}).", released, exception);
            return released;
        }
        if (released > 0) {
            log.info("Retenciones vencidas liberadas: {}", released);
        }
        return released;
    }
}
