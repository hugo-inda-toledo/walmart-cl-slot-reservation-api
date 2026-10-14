package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.reservation.IdempotencyPayloadCodec;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.StoredIdempotencyRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class IdempotencyServiceImplTest {

    private static final CustomerId CUSTOMER = new CustomerId("cust-1");
    private static final IdempotencyKey KEY = new IdempotencyKey("key-1");
    private static final RequestFingerprint FINGERPRINT =
            RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-1");
    private static final RequestFingerprint OTHER_FINGERPRINT =
            RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-2", "order-1");
    private static final IdempotentRequest REQUEST =
            new IdempotentRequest(CUSTOMER, KEY, IdempotentOperation.HOLD_SLOT, FINGERPRINT);
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final int RETRY_AFTER = 3;

    private final FakeIdempotencyRepository repository = new FakeIdempotencyRepository();
    private final FakeCodec codec = new FakeCodec();
    private final List<String> metrics = new ArrayList<>();
    private final ReservationMetrics reservationMetrics = new ReservationMetrics() {
        @Override
        public void lifecycle(ReservationLifecycleEvent.Kind kind, int count) {
            throw new AssertionError("no se esperaba una métrica de ciclo de vida");
        }

        @Override
        public void holdRejected(String code) {
            throw new AssertionError("no se esperaba una métrica de rechazo de retención");
        }

        @Override
        public void idempotency(String result) {
            metrics.add(result);
        }
    };
    private final IdempotencyServiceImpl service = new IdempotencyServiceImpl(repository, codec, reservationMetrics,
            new ReservationSettings(Duration.ofMinutes(10), 3, RETENTION, WAIT, RETRY_AFTER, 100, 5, 100));

    @Test
    void claimedExecutesActionAndFixesSuccessInSameCall() {
        var executions = new AtomicInteger();

        var result = service.execute(REQUEST, String.class, () -> {
            executions.incrementAndGet();
            return "hold-created";
        });

        assertThat(result).isEqualTo("hold-created");
        assertThat(executions).hasValue(1);
        assertThat(repository.completions).hasSize(1);
        var completion = repository.completions.getFirst();
        assertThat(completion.outcome()).isEqualTo(IdempotencyOutcome.SUCCEEDED);
        assertThat(completion.customer()).isEqualTo(CUSTOMER);
        assertThat(completion.key()).isEqualTo(KEY);
        assertThat(completion.payload()).isEqualTo(codec.encode("hold-created"));
        assertThat(metrics).isEmpty();
    }

    @Test
    void claimUsesConfiguredRetentionWaitAndRetryAfter() {
        service.execute(REQUEST, String.class, () -> "ok");

        assertThat(repository.lastRetention).isEqualTo(RETENTION);
        assertThat(repository.lastWait).isEqualTo(WAIT);
        assertThat(repository.lastRetryAfter).isEqualTo(RETRY_AFTER);
        assertThat(repository.lastOperation).isEqualTo(IdempotentOperation.HOLD_SLOT);
        assertThat(repository.lastFingerprint).isEqualTo(FINGERPRINT);
    }

    @Test
    void replaysStoredSuccessWithoutExecutingActionAndCountsReplay() {
        repository.existing = new StoredIdempotencyRecord(IdempotentOperation.HOLD_SLOT, FINGERPRINT,
                IdempotencyOutcome.SUCCEEDED, codec.encode("stored-result"));
        var executions = new AtomicInteger();

        var result = service.execute(REQUEST, String.class, () -> {
            executions.incrementAndGet();
            return "new-result";
        });

        assertThat(result).isEqualTo("stored-result");
        assertThat(executions).hasValue(0);
        assertThat(repository.completions).isEmpty();
        assertThat(metrics).containsExactly("replayed");
    }

    @Test
    void replaysStoredRejectionWithoutExecutingActionAndCountsReplay() {
        var rejection = ReservationException.holdNotFound(new HoldId(java.util.UUID.randomUUID()));
        repository.existing = new StoredIdempotencyRecord(IdempotentOperation.HOLD_SLOT, FINGERPRINT,
                IdempotencyOutcome.REJECTED, codec.encodeRejection(rejection));
        var executions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            executions.incrementAndGet();
            return "new-result";
        })).isSameAs(rejection);

        assertThat(executions).hasValue(0);
        assertThat(repository.completions).isEmpty();
        assertThat(metrics).containsExactly("replayed");
    }

    @Test
    void sameKeyWithDifferentFingerprintIsKeyReusedAndWritesNothing() {
        repository.existing = new StoredIdempotencyRecord(IdempotentOperation.HOLD_SLOT, OTHER_FINGERPRINT,
                IdempotencyOutcome.SUCCEEDED, codec.encode("stored-result"));
        var executions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            executions.incrementAndGet();
            return "new-result";
        })).isInstanceOfSatisfying(ReservationException.class,
                e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(executions).hasValue(0);
        assertThat(repository.completions).isEmpty();
        assertThat(metrics).containsExactly("key_reused");
    }

    @Test
    void sameKeyWithDifferentOperationIsKeyReused() {
        repository.existing = new StoredIdempotencyRecord(IdempotentOperation.CONFIRM_HOLD, FINGERPRINT,
                IdempotencyOutcome.SUCCEEDED, codec.encode("stored-result"));

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> "new-result"))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));

        assertThat(repository.completions).isEmpty();
        assertThat(metrics).containsExactly("key_reused");
    }

    @Test
    void keyReusedIsCheckedBeforeReplayingStoredRejection() {
        repository.existing = new StoredIdempotencyRecord(IdempotentOperation.HOLD_SLOT, OTHER_FINGERPRINT,
                IdempotencyOutcome.REJECTED, codec.encodeRejection(ReservationException.invalidCustomerId()));

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> "x"))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(metrics).containsExactly("key_reused");
    }

    @Test
    void businessRejectionIsFixedAndRethrown() {
        var rejection = ReservationException.invalidCustomerId();

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            throw rejection;
        })).isSameAs(rejection);

        assertThat(repository.completions).hasSize(1);
        var completion = repository.completions.getFirst();
        assertThat(completion.outcome()).isEqualTo(IdempotencyOutcome.REJECTED);
        assertThat(completion.payload()).isEqualTo(codec.encodeRejection(rejection));
        assertThat(metrics).isEmpty();
    }

    @Test
    void internalErrorIsPropagatedWithoutFixingTheKey() {
        var failure = new IllegalStateException("fallo interno");

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(repository.completions).isEmpty();
    }

    @Test
    void raceConflictIsPropagatedWithoutFixingTheKey() {
        var conflict = ReservationConflictException.activeHoldExists(null);

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            throw conflict;
        })).isSameAs(conflict);

        assertThat(repository.completions).isEmpty();
    }

    @Test
    void inProgressKeyCountsMetricAndPropagatesWithoutExecutingNorFixing() {
        repository.claimFailure = new IdempotencyInProgressException(RETRY_AFTER);
        var executions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute(REQUEST, String.class, () -> {
            executions.incrementAndGet();
            return "x";
        })).isSameAs(repository.claimFailure);

        assertThat(executions).hasValue(0);
        assertThat(repository.completions).isEmpty();
        assertThat(metrics).containsExactly("in_progress");
    }

    @Test
    void mustJoinTheCallerTransactionAndKeepBusinessRejectionsCommittable() throws Exception {
        var transactional = IdempotencyServiceImpl.class.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(transactional.noRollbackFor()).containsExactly(ReservationException.class);
    }

    // --- fakes ---

    private record Completion(CustomerId customer, IdempotencyKey key, IdempotencyOutcome outcome, String payload) {
    }

    private static final class FakeIdempotencyRepository implements IdempotencyRepository {

        StoredIdempotencyRecord existing;
        IdempotencyInProgressException claimFailure;
        final List<Completion> completions = new ArrayList<>();
        Duration lastRetention;
        Duration lastWait;
        int lastRetryAfter;
        IdempotentOperation lastOperation;
        RequestFingerprint lastFingerprint;

        @Override
        public IdempotencyClaim claim(CustomerId customer, IdempotencyKey key, IdempotentOperation operation,
                                      RequestFingerprint fingerprint, Duration retention, Duration wait,
                                      int retryAfterSeconds) {
            lastRetention = retention;
            lastWait = wait;
            lastRetryAfter = retryAfterSeconds;
            lastOperation = operation;
            lastFingerprint = fingerprint;
            if (claimFailure != null) {
                throw claimFailure;
            }
            return existing == null ? new IdempotencyClaim.Claimed() : new IdempotencyClaim.Existing(existing);
        }

        @Override
        public void complete(CustomerId customer, IdempotencyKey key, IdempotencyOutcome outcome,
                             String payloadJson) {
            completions.add(new Completion(customer, key, outcome, payloadJson));
        }

        @Override
        public int deleteExpired(int limit) {
            throw new AssertionError("no aplica");
        }
    }

    /** Codec falso: guarda los objetos por referencia bajo un token de texto. */
    private static final class FakeCodec implements IdempotencyPayloadCodec {

        private final Map<String, Object> store = new HashMap<>();

        @Override
        public String encode(Object result) {
            var token = "result:" + result;
            store.put(token, result);
            return token;
        }

        @Override
        public <T> T decode(String payload, Class<T> type) {
            return type.cast(store.get(payload));
        }

        @Override
        public String encodeRejection(ReservationException rejection) {
            var token = "rejection:" + rejection.reservationCode() + ":" + System.identityHashCode(rejection);
            store.put(token, rejection);
            return token;
        }

        @Override
        public ReservationException decodeRejection(String payload) {
            return (ReservationException) store.get(payload);
        }
    }
}
