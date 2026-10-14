package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.usecase.reservation.PurgeExpiredIdempotencyKeysUseCase;
import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class IdempotencyKeyPurgerTest {

    private static final int BATCH_SIZE = 100;

    private final ScriptedUseCase useCase = new ScriptedUseCase();
    private final IdempotencyKeyPurger purger = new IdempotencyKeyPurger(useCase, new ReservationSettings(
            Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(2), 1, 10, 3, BATCH_SIZE));
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(IdempotencyKeyPurger.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("repite lotes mientras vengan llenos hasta el primero incompleto")
    void repeatsWhileBatchesComeBackFull() {
        useCase.script(BATCH_SIZE, BATCH_SIZE, 5);

        assertThat(purger.purge()).isEqualTo(2 * BATCH_SIZE + 5);
        assertThat(useCase.calls).isEqualTo(3);
    }

    @Test
    @DisplayName("captura la falla y la registra en ERROR sin propagarla")
    void capturesFailure() {
        useCase.script(new IllegalStateException("db caída"));

        assertThatCode(purger::purge).doesNotThrowAnyException();
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    private static final class ScriptedUseCase implements PurgeExpiredIdempotencyKeysUseCase {

        private final Deque<Object> outcomes = new ArrayDeque<>();
        int calls;

        void script(Object... values) {
            outcomes.addAll(List.of(values));
        }

        @Override
        public int purgeExpired() {
            calls++;
            Object next = outcomes.isEmpty() ? 0 : outcomes.poll();
            if (next instanceof RuntimeException exception) {
                throw exception;
            }
            return (Integer) next;
        }
    }
}
