package cl.walmart.slot.reservation.application.listener.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cl.walmart.slot.reservation.domain.port.catalog.AvailabilityCacheInvalidationPort;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent.Kind;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ReservationAfterCommitListenerTest {

    private static final ZoneDay DAY_A = new ZoneDay(UUID.randomUUID(), LocalDate.of(2031, 3, 5));
    private static final ZoneDay DAY_B = new ZoneDay(UUID.randomUUID(), LocalDate.of(2031, 3, 6));

    private final List<ZoneDay> invalidated = new ArrayList<>();
    private final List<String> lifecycle = new ArrayList<>();

    private final AvailabilityCacheInvalidationPort port =
            (zoneId, date) -> invalidated.add(new ZoneDay(zoneId, date));
    private final ReservationMetrics metrics = new ReservationMetrics() {
        @Override
        public void lifecycle(Kind kind, int count) {
            lifecycle.add(kind + ":" + count);
        }

        @Override
        public void holdRejected(String code) {
        }

        @Override
        public void idempotency(String result) {
        }
    };

    @Test
    void listensAfterCommitOnly() throws Exception {
        Method method = ReservationAfterCommitListener.class.getDeclaredMethod("on", ReservationLifecycleEvent.class);

        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void invalidatesEveryChangedZoneDayAndRecordsLifecycleMetrics() {
        new ReservationAfterCommitListener(port, metrics)
                .on(new ReservationLifecycleEvent(Kind.HOLD_CREATED, 1, Set.of(DAY_A, DAY_B)));

        assertThat(invalidated).containsExactlyInAnyOrder(DAY_A, DAY_B);
        assertThat(lifecycle).containsExactly("HOLD_CREATED:1");
    }

    @Test
    void aFailingNoticeIsNotPropagatedAndTheRestIsStillProcessed() {
        AvailabilityCacheInvalidationPort failing = (zoneId, date) -> {
            invalidated.add(new ZoneDay(zoneId, date));
            throw new IllegalStateException("cache down");
        };
        var listener = new ReservationAfterCommitListener(failing, metrics);

        assertThatCode(() -> listener.on(new ReservationLifecycleEvent(Kind.HOLD_EXPIRED, 2, Set.of(DAY_A, DAY_B))))
                .doesNotThrowAnyException();

        assertThat(invalidated).hasSize(2);
        assertThat(lifecycle).containsExactly("HOLD_EXPIRED:2");
    }

    @Test
    void noNoticeNorMetricWhenThePublishingTransactionRollsBack() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("port", port);
            context.getBeanFactory().registerSingleton("metrics", metrics);
            context.registerBean(org.springframework.transaction.event.TransactionalEventListenerFactory.class);
            context.register(ReservationAfterCommitListener.class);
            context.refresh();
            var template = new TransactionTemplate(new TestTransactionManager());

            template.executeWithoutResult(status -> {
                context.publishEvent(new ReservationLifecycleEvent(Kind.HOLD_CREATED, 1, Set.of(DAY_A)));
                status.setRollbackOnly();
            });
            assertThat(invalidated).isEmpty();
            assertThat(lifecycle).isEmpty();

            template.executeWithoutResult(status ->
                    context.publishEvent(new ReservationLifecycleEvent(Kind.HOLD_CREATED, 1, Set.of(DAY_A))));
            assertThat(invalidated).containsExactly(DAY_A);
            assertThat(lifecycle).containsExactly("HOLD_CREATED:1");
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
