package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.TestPropertySource;

/**
 * Purgador habilitado con cron de cada segundo y lotes chicos (requisito 9.10): contra PostgreSQL real, las claves
 * vencidas se borran en varios lotes dentro de una misma pasada y las vigentes quedan intactas. Al terminar se
 * cancela la tarea programada sin cerrar el contexto (cerrarlo detendría el contenedor compartido).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.reservation.idempotency.purge-enabled=true",
        "app.reservation.idempotency.purge-cron=* * * * * *",
        "app.reservation.idempotency.purge-batch-size=2"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyKeyPurgerIT {

    private static final int EXPIRED_KEYS = 5;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IdempotencyKeyPurger purger;
    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTasks;

    private ReservationTestSlots slots;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
    }

    @AfterAll
    void stopPurger() {
        scheduledTasks.postProcessBeforeDestruction(purger, "idempotencyKeyPurger");
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    @DisplayName("el cron borra las claves vencidas en varios lotes y deja intactas las vigentes")
    void purgesExpiredKeysInSeveralBatchesAndKeepsLiveOnes() {
        String customer = slots.newCustomerId();
        for (int i = 0; i < EXPIRED_KEYS; i++) {
            insertKey(customer, "expired-" + i, "now() - interval '1 day'");
        }
        insertKey(customer, "live-1", "now() + interval '1 day'");
        insertKey(customer, "live-2", "now() + interval '1 hour'");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(keysOf(customer)).containsExactlyInAnyOrder("live-1", "live-2"));
    }

    private void insertKey(String customer, String key, String expiresAtSql) {
        jdbc.update("insert into reservation_idempotency_key (customer_id, idempotency_key, operation, "
                + "request_fingerprint, outcome, response_payload, created_at, expires_at) "
                + "values (?, ?, 'HOLD_SLOT', repeat('a', 64), 'SUCCEEDED', '{}'::jsonb, "
                + "now() - interval '2 days', " + expiresAtSql + ")", customer, key);
    }

    private List<String> keysOf(String customer) {
        return jdbc.queryForList("select idempotency_key from reservation_idempotency_key where customer_id = ?",
                String.class, customer);
    }
}
