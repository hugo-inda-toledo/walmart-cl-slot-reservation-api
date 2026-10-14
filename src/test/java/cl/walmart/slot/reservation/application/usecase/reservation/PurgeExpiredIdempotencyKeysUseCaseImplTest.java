package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PurgeExpiredIdempotencyKeysUseCaseImplTest {

    private static final int PURGE_BATCH = 250;

    private final FakeIdempotencyRepository keys = new FakeIdempotencyRepository();
    private final PurgeExpiredIdempotencyKeysUseCaseImpl useCase = new PurgeExpiredIdempotencyKeysUseCaseImpl(keys,
            new ReservationSettings(Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(2), 1,
                    100, 5, PURGE_BATCH));

    @Test
    @DisplayName("purga un lote con el tamaño configurado y devuelve la cantidad borrada")
    void purgesOneBatchWithConfiguredSizeAndReturnsCount() {
        keys.nextDeleted = 42;

        int purged = useCase.purgeExpired();

        assertThat(purged).isEqualTo(42);
        assertThat(keys.requestedLimits).containsExactly(PURGE_BATCH);
    }

    @Test
    @DisplayName("sin claves vencidas devuelve cero")
    void returnsZeroWhenNothingExpired() {
        assertThat(useCase.purgeExpired()).isZero();
    }

    @Test
    @DisplayName("es el límite transaccional: un lote por transacción")
    void isTransactionalBoundary() {
        assertThat(PurgeExpiredIdempotencyKeysUseCaseImpl.class.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static final class FakeIdempotencyRepository implements IdempotencyRepository {

        final List<Integer> requestedLimits = new ArrayList<>();
        int nextDeleted;

        @Override
        public IdempotencyClaim claim(CustomerId customer, IdempotencyKey key, IdempotentOperation operation,
                                      RequestFingerprint fingerprint, Duration retention, Duration wait,
                                      int retryAfterSeconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(CustomerId customer, IdempotencyKey key, IdempotencyOutcome outcome,
                             String payloadJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteExpired(int limit) {
            requestedLimits.add(limit);
            return nextDeleted;
        }
    }
}
