package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
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

class ExpiredHoldSweeperTest {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_BATCHES = 3;

    private final ScriptedUseCase useCase = new ScriptedUseCase();
    private final ExpiredHoldSweeper sweeper = new ExpiredHoldSweeper(useCase, new ReservationSettings(
            Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(2), 1, BATCH_SIZE, MAX_BATCHES, 100));
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(ExpiredHoldSweeper.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("repite lotes mientras vengan llenos y se detiene en el primero incompleto")
    void repeatsWhileBatchesComeBackFull() {
        useCase.script(BATCH_SIZE, 4);

        int released = sweeper.sweep();

        assertThat(released).isEqualTo(BATCH_SIZE + 4);
        assertThat(useCase.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("no pasa del máximo de lotes por ejecución aunque sigan llenos")
    void stopsAtTheMaximumBatchesPerRun() {
        useCase.script(BATCH_SIZE, BATCH_SIZE, BATCH_SIZE, BATCH_SIZE, BATCH_SIZE);

        int released = sweeper.sweep();

        assertThat(useCase.calls).isEqualTo(MAX_BATCHES);
        assertThat(released).isEqualTo(BATCH_SIZE * MAX_BATCHES);
    }

    @Test
    @DisplayName("sin vencidas hace una sola llamada y no registra nada en INFO")
    void doesNothingWhenNothingIsDue() {
        useCase.script(0);

        assertThat(sweeper.sweep()).isZero();

        assertThat(useCase.calls).isEqualTo(1);
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.INFO, Level.ERROR);
    }

    @Test
    @DisplayName("registra en INFO la cantidad liberada cuando es mayor que cero")
    void logsReleasedCount() {
        useCase.script(7);

        sweeper.sweep();

        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("7");
        });
    }

    @Test
    @DisplayName("captura la falla, la registra en ERROR y conserva lo liberado; la siguiente ejecución funciona")
    void capturesFailureAndNextRunWorks() {
        useCase.script(BATCH_SIZE, new IllegalStateException("db caída"));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));

        useCase.script(3);
        assertThat(sweeper.sweep()).isEqualTo(3);
    }

    private static final class ScriptedUseCase implements ReleaseExpiredHoldsUseCase {

        private final Deque<Object> outcomes = new ArrayDeque<>();
        int calls;

        void script(Object... values) {
            outcomes.clear();
            calls = 0;
            outcomes.addAll(List.of(values));
        }

        @Override
        public int releaseExpired() {
            calls++;
            Object next = outcomes.isEmpty() ? 0 : outcomes.poll();
            if (next instanceof RuntimeException exception) {
                throw exception;
            }
            return (Integer) next;
        }
    }
}
