package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import cl.walmart.slot.reservation.domain.entities.reservation.ExpiredBatch;
import cl.walmart.slot.reservation.domain.service.reservation.HoldService;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ReleaseExpiredHoldsUseCaseImplTest {

    private static final int BATCH_SIZE = 37;

    private final FakeHoldService holds = new FakeHoldService();
    private final ReleaseExpiredHoldsUseCaseImpl useCase = new ReleaseExpiredHoldsUseCaseImpl(holds,
            new ReservationSettings(Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(2), 1,
                    BATCH_SIZE, 5, 100));

    @Test
    @DisplayName("libera un lote con el tamaño configurado y devuelve la cantidad liberada")
    void releasesOneBatchWithConfiguredSizeAndReturnsCount() {
        holds.nextCount = 12;

        int released = useCase.releaseExpired();

        assertThat(released).isEqualTo(12);
        assertThat(holds.requestedLimits).containsExactly(BATCH_SIZE);
    }

    @Test
    @DisplayName("cada invocación procesa un solo lote: el bucle pertenece al scheduler")
    void eachCallProcessesExactlyOneBatch() {
        holds.nextCount = BATCH_SIZE;

        useCase.releaseExpired();
        useCase.releaseExpired();

        assertThat(holds.requestedLimits).containsExactly(BATCH_SIZE, BATCH_SIZE);
    }

    @Test
    @DisplayName("sin retenciones vencidas devuelve cero")
    void returnsZeroWhenNothingIsDue() {
        holds.nextCount = 0;

        assertThat(useCase.releaseExpired()).isZero();
    }

    @Test
    @DisplayName("es el límite transaccional: un lote por transacción")
    void isTransactionalBoundary() {
        assertThat(ReleaseExpiredHoldsUseCaseImpl.class.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static final class FakeHoldService implements HoldService {

        final List<Integer> requestedLimits = new ArrayList<>();
        int nextCount;

        @Override
        public Optional<ZoneDay> expireStaleForOrder(CustomerId customer, OrderId order) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpiredBatch expireDue(int limit) {
            requestedLimits.add(limit);
            return new ExpiredBatch(nextCount, Set.of());
        }
    }
}
