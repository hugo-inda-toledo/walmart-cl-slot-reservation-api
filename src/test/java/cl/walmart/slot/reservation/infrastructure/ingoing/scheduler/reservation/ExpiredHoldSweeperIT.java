package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Barredor habilitado con intervalo corto (requisitos 7.1, 7.3, 7.6, 7.7, 7.8): una retención vencida queda
 * liberada y su cupo vuelve dentro de vencimiento + intervalo; una falla inducida se registra y la ejecución
 * siguiente funciona. Al terminar se cancela la tarea programada del barredor (sin cerrar el contexto, porque
 * cerrarlo detendría el contenedor PostgreSQL compartido por el resto de los IT) para que no siga corriendo sobre
 * los datos de otras clases de test.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.reservation.sweeper.enabled=true",
        "app.reservation.sweeper.fixed-delay=PT1S"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpiredHoldSweeperIT {

    private static final Duration INTERVAL = Duration.ofSeconds(1);

    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private ReleaseExpiredHoldsUseCase releaseExpiredHolds;
    @Autowired
    private ExpiredHoldSweeper sweeper;
    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTasks;

    private ReservationTestSlots slots;
    private ReservationInvariants invariants;
    private ListAppender<ILoggingEvent> logs;
    private Logger sweeperLogger;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        invariants = new ReservationInvariants(jdbc);
        logs = new ListAppender<>();
        logs.start();
        sweeperLogger = (Logger) LoggerFactory.getLogger(ExpiredHoldSweeper.class);
        sweeperLogger.addAppender(logs);
    }

    @AfterAll
    void stopSweeper() {
        scheduledTasks.postProcessBeforeDestruction(sweeper, "expiredHoldSweeper");
    }

    @AfterEach
    void tearDown() {
        sweeperLogger.detachAppender(logs);
        Mockito.reset(releaseExpiredHolds);
        slots.cleanup();
    }

    @Test
    @DisplayName("una retención vencida queda EXPIRED y su cupo vuelve dentro de vencimiento + intervalo")
    void releasesAnExpiredHoldAndReturnsItsCapacity() {
        TestSlot slot = slots.createSlot(3);
        UUID holdId = insertExpiredHold(slot, 1);
        assertThat(invariants.countersOf(java.util.List.of(slot.id())).getFirst().held()).isEqualTo(1);

        await().atMost(INTERVAL.multipliedBy(10)).untilAsserted(() ->
                assertThat(statusOf(holdId)).isEqualTo("EXPIRED"));

        assertThat(heldOf(slot)).isZero();
        assertThat(reservedOf(slot)).isZero();
        invariants.assertConserved(slot.id());
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("liberadas");
        });
    }

    @Test
    @DisplayName("una falla inducida se registra en ERROR y la ejecución siguiente libera la retención")
    void recordsAnInducedFailureAndTheNextRunWorks() {
        AtomicInteger failures = new AtomicInteger();
        Mockito.doAnswer(invocation -> {
            if (failures.getAndIncrement() < 2) {
                throw new IllegalStateException("falla inducida");
            }
            return invocation.callRealMethod();
        }).when(releaseExpiredHolds).releaseExpired();
        TestSlot slot = slots.createSlot(3);
        UUID holdId = insertExpiredHold(slot, 1);

        await().atMost(INTERVAL.multipliedBy(15)).untilAsserted(() ->
                assertThat(statusOf(holdId)).isEqualTo("EXPIRED"));

        assertThat(failures.get()).isGreaterThanOrEqualTo(3);
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).contains("falla inducida");
        });
        assertThat(heldOf(slot)).isZero();
        invariants.assertConserved(slot.id());
        Mockito.verify(releaseExpiredHolds, Mockito.atLeast(3)).releaseExpired();
    }

    /** Retención ACTIVE ya vencida según la base y su cupo retenido en la ventana. */
    private UUID insertExpiredHold(TestSlot slot, int secondsOverdue) {
        UUID id = UUID.randomUUID();
        jdbc.update("update catalog_delivery_slot set held = held + 1 where id = ?", slot.id());
        jdbc.update("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                + "start_at, end_at, cutoff_at, price_clp, status, created_at, expires_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now() - interval '1 day', "
                + "now() - make_interval(secs => ?))",
                id, slots.newCustomerId(), slots.newOrderId(), slot.id(), slot.zoneId(), slot.deliveryDate(),
                OffsetDateTime.ofInstant(slot.startAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.endAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.cutoffAt(), ZoneOffset.UTC),
                slot.priceClp(), (double) secondsOverdue);
        return id;
    }

    private String statusOf(UUID holdId) {
        return jdbc.queryForObject("select status from reservation_slot_hold where id = ?", String.class, holdId);
    }

    private int heldOf(TestSlot slot) {
        return jdbc.queryForObject("select held from catalog_delivery_slot where id = ?", Integer.class, slot.id());
    }

    private int reservedOf(TestSlot slot) {
        return jdbc.queryForObject("select reserved from catalog_delivery_slot where id = ?", Integer.class,
                slot.id());
    }
}
