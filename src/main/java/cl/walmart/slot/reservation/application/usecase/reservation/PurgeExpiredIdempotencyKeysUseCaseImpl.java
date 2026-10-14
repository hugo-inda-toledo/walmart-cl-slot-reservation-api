package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import cl.walmart.slot.reservation.domain.usecase.reservation.PurgeExpiredIdempotencyKeysUseCase;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional de la purga: un lote por transacción, con la hora de la base de datos. Usa el repositorio
 * directamente (un servicio intermedio sería un pass-through).
 */
@Service
@Transactional
class PurgeExpiredIdempotencyKeysUseCaseImpl implements PurgeExpiredIdempotencyKeysUseCase {

    private final IdempotencyRepository keys;
    private final ReservationSettings settings;

    PurgeExpiredIdempotencyKeysUseCaseImpl(IdempotencyRepository keys, ReservationSettings settings) {
        this.keys = keys;
        this.settings = settings;
    }

    @Override
    public int purgeExpired() {
        return keys.deleteExpired(settings.purgeBatchSize());
    }
}
