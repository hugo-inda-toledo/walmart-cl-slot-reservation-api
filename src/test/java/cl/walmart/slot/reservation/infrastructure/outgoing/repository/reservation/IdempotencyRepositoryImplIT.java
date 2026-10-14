package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import cl.walmart.slot.reservation.domain.entities.reservation.StoredIdempotencyRecord;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdempotencyRepositoryImplIT {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final int RETRY_AFTER = 7;
    private static final String PAYLOAD = "{\"v\":1,\"type\":\"HoldView\",\"body\":{\"orderId\":\"o-1\"}}";

    @Autowired
    private IdempotencyRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ReservationTestSlots slots;
    private TransactionTemplate tx;
    private CustomerId customer;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        tx = new TransactionTemplate(transactionManager);
        customer = new CustomerId(slots.newCustomerId());
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    void claimInsertsAnInFlightRowExpiringAfterTheRetentionOnTheDatabaseClock() {
        IdempotencyKey key = key();

        IdempotencyClaim claim = tx.execute(s -> claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT));

        assertThat(claim).isEqualTo(new IdempotencyClaim.Claimed());
        var row = jdbc.queryForMap("select operation, request_fingerprint, outcome, response_payload, "
                + "extract(epoch from (expires_at - created_at)) as retention_seconds "
                + "from reservation_idempotency_key where customer_id = ? and idempotency_key = ?",
                customer.value(), key.value());
        assertThat(row.get("operation")).isEqualTo("HOLD_SLOT");
        assertThat(row.get("request_fingerprint")).isEqualTo(fingerprint(IdempotentOperation.HOLD_SLOT, "a").value());
        assertThat(row.get("outcome")).isNull();
        assertThat(row.get("response_payload")).isNull();
        assertThat(((Number) row.get("retention_seconds")).longValue()).isEqualTo(RETENTION.toSeconds());
    }

    @Test
    void completeFixesTheOutcomeAndPayloadAndLaterClaimsSeeTheStoredRecord() {
        IdempotencyKey key = key();
        tx.executeWithoutResult(s -> {
            claim(customer, key, IdempotentOperation.CONFIRM_HOLD, "h", WAIT);
            repository.complete(customer, key, IdempotencyOutcome.SUCCEEDED, PAYLOAD);
        });

        IdempotencyClaim again = tx.execute(s -> claim(customer, key, IdempotentOperation.CONFIRM_HOLD, "h", WAIT));

        assertThat(again).isInstanceOfSatisfying(IdempotencyClaim.Existing.class, existing -> {
            StoredIdempotencyRecord record = existing.record();
            assertThat(record.operation()).isEqualTo(IdempotentOperation.CONFIRM_HOLD);
            assertThat(record.fingerprint()).isEqualTo(fingerprint(IdempotentOperation.CONFIRM_HOLD, "h"));
            assertThat(record.outcome()).isEqualTo(IdempotencyOutcome.SUCCEEDED);
            assertThat(record.payloadJson()).contains("\"orderId\"").contains("\"o-1\"");
        });
    }

    @Test
    void rejectedOutcomeIsStoredAndReturned() {
        IdempotencyKey key = key();
        tx.executeWithoutResult(s -> {
            claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT);
            repository.complete(customer, key, IdempotencyOutcome.REJECTED, "{\"v\":1,\"code\":\"SLOT_FULL\"}");
        });

        IdempotencyClaim again = tx.execute(s -> claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT));

        assertThat(again).isInstanceOfSatisfying(IdempotencyClaim.Existing.class,
                existing -> assertThat(existing.record().outcome()).isEqualTo(IdempotencyOutcome.REJECTED));
    }

    @Test
    void keysAreScopedPerCustomer() {
        IdempotencyKey key = key();
        CustomerId other = new CustomerId(slots.newCustomerId());

        IdempotencyClaim first = tx.execute(s -> claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT));
        IdempotencyClaim second = tx.execute(s -> claim(other, key, IdempotentOperation.HOLD_SLOT, "a", WAIT));

        assertThat(first).isInstanceOf(IdempotencyClaim.Claimed.class);
        assertThat(second).isInstanceOf(IdempotencyClaim.Claimed.class);
    }

    @Test
    void completeWithoutAClaimedKeyFails() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                repository.complete(customer, key(), IdempotencyOutcome.SUCCEEDED, PAYLOAD)))
                // El proxy de @Repository traduce IllegalStateException a InvalidDataAccessApiUsageException.
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("not claimed");
    }

    @Test
    void lockTimeoutIsRestoredToItsPreviousValueAfterTheClaim() {
        tx.executeWithoutResult(s -> {
            jdbc.execute("set local lock_timeout = '1234ms'");

            claim(customer, key(), IdempotentOperation.HOLD_SLOT, "a", Duration.ofMillis(250));

            assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo("1234ms");
        });
        tx.executeWithoutResult(s -> {
            claim(customer, key(), IdempotentOperation.HOLD_SLOT, "a", Duration.ofMillis(250));

            assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo("0");
        });
    }

    @Test
    void secondTransactionWaitsForTheFirstAndSeesTheCompletedRow() throws Exception {
        IdempotencyKey key = key();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(s -> {
                claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT);
                firstClaimed.countDown();
                await(secondStarted);
                sleep(700);
                repository.complete(customer, key, IdempotencyOutcome.SUCCEEDED, PAYLOAD);
            }));
            assertThat(firstClaimed.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Timed> second = pool.submit(() -> {
                secondStarted.countDown();
                long started = System.nanoTime();
                IdempotencyClaim claim = tx.execute(s -> claim(customer, key, IdempotentOperation.HOLD_SLOT, "a",
                        WAIT));
                return new Timed(claim, Duration.ofNanos(System.nanoTime() - started));
            });

            Timed result = second.get(30, TimeUnit.SECONDS);
            first.get(30, TimeUnit.SECONDS);

            assertThat(result.elapsed()).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            assertThat(result.claim()).isInstanceOfSatisfying(IdempotencyClaim.Existing.class, existing ->
                    assertThat(existing.record().outcome()).isEqualTo(IdempotencyOutcome.SUCCEEDED));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void secondTransactionClaimsWhenTheFirstRollsBack() throws Exception {
        IdempotencyKey key = key();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(s -> {
                claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT);
                firstClaimed.countDown();
                await(secondStarted);
                sleep(500);
                s.setRollbackOnly();
            }));
            assertThat(firstClaimed.await(30, TimeUnit.SECONDS)).isTrue();
            Future<IdempotencyClaim> second = pool.submit(() -> {
                secondStarted.countDown();
                return tx.execute(s -> claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT));
            });

            assertThat(second.get(30, TimeUnit.SECONDS)).isInstanceOf(IdempotencyClaim.Claimed.class);
            first.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void exhaustedWaitRaisesTheInProgressExceptionWithTheSuggestedSeconds() throws Exception {
        IdempotencyKey key = key();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(s -> {
                claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT);
                firstClaimed.countDown();
                await(secondDone);
            }));
            assertThat(firstClaimed.await(30, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                    claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", Duration.ofMillis(300))))
                    .isInstanceOfSatisfying(IdempotencyInProgressException.class, e -> {
                        assertThat(e.retryAfterSeconds()).isEqualTo(RETRY_AFTER);
                        assertThat(e.details()).containsEntry("retryAfterSeconds", RETRY_AFTER);
                    });
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            secondDone.countDown();
            first.get(30, TimeUnit.SECONDS);

            assertThat(elapsed).isBetween(Duration.ofMillis(250), Duration.ofSeconds(8));
            assertThat(jdbc.queryForObject("select count(*) from reservation_idempotency_key "
                    + "where customer_id = ? and idempotency_key = ?", Integer.class, customer.value(),
                    key.value())).isEqualTo(1);
        } finally {
            secondDone.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void expiredKeyIsRecycledWithTheNewOperationAndFingerprint() {
        IdempotencyKey key = key();
        tx.executeWithoutResult(s -> {
            claim(customer, key, IdempotentOperation.HOLD_SLOT, "a", WAIT);
            repository.complete(customer, key, IdempotencyOutcome.SUCCEEDED, PAYLOAD);
        });
        expire(customer, key);

        IdempotencyClaim claim = tx.execute(s -> claim(customer, key, IdempotentOperation.CONFIRM_HOLD, "b", WAIT));

        assertThat(claim).isInstanceOf(IdempotencyClaim.Claimed.class);
        var row = jdbc.queryForMap("select operation, request_fingerprint, outcome, response_payload, "
                + "expires_at > now() as live from reservation_idempotency_key "
                + "where customer_id = ? and idempotency_key = ?", customer.value(), key.value());
        assertThat(row.get("operation")).isEqualTo("CONFIRM_HOLD");
        assertThat(row.get("request_fingerprint"))
                .isEqualTo(fingerprint(IdempotentOperation.CONFIRM_HOLD, "b").value());
        assertThat(row.get("outcome")).isNull();
        assertThat(row.get("response_payload")).isNull();
        assertThat(row.get("live")).isEqualTo(true);
    }

    @Test
    void purgeDeletesOnlyExpiredKeysInBatches() {
        IdempotencyKey live = key();
        IdempotencyKey a = key();
        IdempotencyKey b = key();
        IdempotencyKey c = key();
        for (IdempotencyKey k : new IdempotencyKey[] {live, a, b, c}) {
            tx.executeWithoutResult(s -> {
                claim(customer, k, IdempotentOperation.HOLD_SLOT, "a", WAIT);
                repository.complete(customer, k, IdempotencyOutcome.SUCCEEDED, PAYLOAD);
            });
        }
        expire(customer, a);
        expire(customer, b);
        expire(customer, c);

        int firstBatch = tx.execute(s -> repository.deleteExpired(2));
        int secondBatch = tx.execute(s -> repository.deleteExpired(2));
        int thirdBatch = tx.execute(s -> repository.deleteExpired(2));

        assertThat(firstBatch).isEqualTo(2);
        assertThat(secondBatch).isEqualTo(1);
        assertThat(thirdBatch).isZero();
        assertThat(jdbc.queryForList("select idempotency_key from reservation_idempotency_key "
                + "where customer_id = ?", String.class, customer.value())).containsExactly(live.value());
    }

    private IdempotencyClaim claim(CustomerId who, IdempotencyKey key, IdempotentOperation operation, String field,
                                   Duration wait) {
        return repository.claim(who, key, operation, fingerprint(operation, field), RETENTION, wait, RETRY_AFTER);
    }

    private static RequestFingerprint fingerprint(IdempotentOperation operation, String field) {
        return RequestFingerprint.of(operation, field);
    }

    private void expire(CustomerId who, IdempotencyKey key) {
        jdbc.update("update reservation_idempotency_key set created_at = now() - interval '2 days', "
                + "expires_at = now() - interval '1 day' where customer_id = ? and idempotency_key = ?",
                who.value(), key.value());
    }

    private static IdempotencyKey key() {
        return new IdempotencyKey("key-" + UUID.randomUUID());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Timed(IdempotencyClaim claim, Duration elapsed) {
    }
}
