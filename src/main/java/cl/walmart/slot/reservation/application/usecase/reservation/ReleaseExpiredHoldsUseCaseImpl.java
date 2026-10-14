package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import cl.walmart.slot.reservation.domain.service.reservation.HoldService;
import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional del barredor: cada invocación libera un lote en una transacción, de modo que una falla
 * revierte solo ese lote y los demás cupos devueltos no se pierden. El reparto entre instancias lo resuelve
 * {@code SKIP LOCKED} en el repositorio.
 */
@Service
@Transactional
class ReleaseExpiredHoldsUseCaseImpl implements ReleaseExpiredHoldsUseCase {

    private final HoldService holds;
    private final ReservationSettings settings;

    ReleaseExpiredHoldsUseCaseImpl(HoldService holds, ReservationSettings settings) {
        this.holds = holds;
        this.settings = settings;
    }

    @Override
    public int releaseExpired() {
        return holds.expireDue(settings.sweeperBatchSize()).count();
    }
}
