package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.service.reservation.IdempotencyService;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.port.reservation.IdempotencyPayloadCodec;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.StoredIdempotencyRecord;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclamo, repetición y registro del resultado dentro de la transacción del caso de uso: si esa transacción
 * revierte, la clave no queda. Solo los rechazos de negocio ({@link ReservationException}) se fijan; los errores
 * internos, los conflictos por carrera y la clave en curso se propagan sin completar.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ReservationException.class)
class IdempotencyServiceImpl implements IdempotencyService {

    private static final String REPLAYED = "replayed";
    private static final String KEY_REUSED = "key_reused";
    private static final String IN_PROGRESS = "in_progress";

    private final IdempotencyRepository repository;
    private final IdempotencyPayloadCodec codec;
    private final ReservationMetrics metrics;
    private final ReservationSettings settings;

    IdempotencyServiceImpl(IdempotencyRepository repository, IdempotencyPayloadCodec codec,
                           ReservationMetrics metrics, ReservationSettings settings) {
        this.repository = repository;
        this.codec = codec;
        this.metrics = metrics;
        this.settings = settings;
    }

    @Override
    public <T> T execute(IdempotentRequest request, Class<T> resultType, Supplier<T> action) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return switch (claim(request)) {
            case IdempotencyClaim.Existing existing -> replay(request, existing.record(), resultType);
            case IdempotencyClaim.Claimed claimed -> executeAndFix(request, action);
        };
    }

    private IdempotencyClaim claim(IdempotentRequest request) {
        try {
            return repository.claim(request.customer(), request.key(), request.operation(), request.fingerprint(),
                    settings.idempotencyRetention(), settings.idempotencyWaitTimeout(),
                    settings.idempotencyRetryAfterSeconds());
        } catch (IdempotencyInProgressException inProgress) {
            metrics.idempotency(IN_PROGRESS);
            throw inProgress;
        }
    }

    private <T> T replay(IdempotentRequest request, StoredIdempotencyRecord stored, Class<T> resultType) {
        if (stored.operation() != request.operation() || !stored.fingerprint().equals(request.fingerprint())) {
            metrics.idempotency(KEY_REUSED);
            throw ReservationException.idempotencyKeyReused();
        }
        metrics.idempotency(REPLAYED);
        if (stored.outcome() == IdempotencyOutcome.REJECTED) {
            throw codec.decodeRejection(stored.payloadJson());
        }
        return codec.decode(stored.payloadJson(), resultType);
    }

    private <T> T executeAndFix(IdempotentRequest request, Supplier<T> action) {
        T result;
        try {
            result = action.get();
        } catch (ReservationException rejection) {
            repository.complete(request.customer(), request.key(), IdempotencyOutcome.REJECTED,
                    codec.encodeRejection(rejection));
            throw rejection;
        }
        repository.complete(request.customer(), request.key(), IdempotencyOutcome.SUCCEEDED,
                codec.encode(Objects.requireNonNull(result, "el resultado de la acción no puede ser nulo")));
        return result;
    }
}
