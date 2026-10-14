package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.IdempotencyRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import cl.walmart.slot.reservation.domain.entities.reservation.StoredIdempotencyRecord;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Claves de idempotencia solo en PostgreSQL. El reclamo es un único {@code INSERT ... ON CONFLICT DO UPDATE}
 * que recicla la fila solo si está vencida según el {@code now()} de la base; si otra transacción tiene la
 * misma clave sin terminar, la inserción espera su fin con un {@code lock_timeout} local a la transacción que se
 * restaura al valor previo después del reclamo. La espera agotada (SQLSTATE {@code 55P03}) se traduce a
 * {@link IdempotencyInProgressException}; la transacción queda abortada y el caso de uso revierte.
 */
@Repository
class IdempotencyRepositoryImpl implements IdempotencyRepository {

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private static final String CURRENT_LOCK_TIMEOUT = "SELECT current_setting('lock_timeout')";

    /** {@code set_config(..., true)} equivale a {@code SET LOCAL} y admite parámetro enlazado. */
    private static final String SET_LOCAL_LOCK_TIMEOUT = "SELECT set_config('lock_timeout', :value, true)";

    private static final String CLAIM = """
            INSERT INTO reservation_idempotency_key (customer_id, idempotency_key, operation, request_fingerprint,
                                                     created_at, expires_at)
            VALUES (:customer, :key, :operation, :fingerprint, now(), now() + make_interval(secs => :retentionSeconds))
            ON CONFLICT (customer_id, idempotency_key) DO UPDATE
               SET operation = EXCLUDED.operation,
                   request_fingerprint = EXCLUDED.request_fingerprint,
                   outcome = NULL,
                   response_payload = NULL,
                   created_at = now(),
                   expires_at = EXCLUDED.expires_at
             WHERE reservation_idempotency_key.expires_at <= now()
            RETURNING 1
            """;

    private static final String SELECT_LIVE = """
            SELECT operation, request_fingerprint, outcome, response_payload::text AS response_payload
              FROM reservation_idempotency_key
             WHERE customer_id = :customer AND idempotency_key = :key
            """;

    private static final String COMPLETE = """
            UPDATE reservation_idempotency_key
               SET outcome = :outcome, response_payload = CAST(:payload AS jsonb)
             WHERE customer_id = :customer AND idempotency_key = :key AND outcome IS NULL
            """;

    private static final String DELETE_EXPIRED = """
            WITH due AS (SELECT customer_id, idempotency_key
                           FROM reservation_idempotency_key
                          WHERE expires_at <= now()
                          ORDER BY expires_at
                          LIMIT :limit
                            FOR UPDATE SKIP LOCKED)
            DELETE FROM reservation_idempotency_key k
             USING due
             WHERE k.customer_id = due.customer_id AND k.idempotency_key = due.idempotency_key
            """;

    private final JdbcClient jdbc;

    IdempotencyRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public IdempotencyClaim claim(CustomerId customer, IdempotencyKey key, IdempotentOperation operation,
                                  RequestFingerprint fingerprint, Duration retention, Duration wait,
                                  int retryAfterSeconds) {
        requirePositive(retention, "retention");
        requirePositive(wait, "wait");
        try {
            String previousTimeout = jdbc.sql(CURRENT_LOCK_TIMEOUT).query(String.class).single();
            setLockTimeout(Math.max(1, wait.toMillis()) + "ms");
            Optional<Integer> inserted = jdbc.sql(CLAIM)
                    .param("customer", customer.value())
                    .param("key", key.value())
                    .param("operation", operation.name())
                    .param("fingerprint", fingerprint.value())
                    .param("retentionSeconds", retention.toMillis() / 1000.0)
                    .query(Integer.class).optional();
            if (inserted.isPresent()) {
                setLockTimeout(previousTimeout);
                return new IdempotencyClaim.Claimed();
            }
            Optional<StoredIdempotencyRecord> existing = jdbc.sql(SELECT_LIVE)
                    .param("customer", customer.value())
                    .param("key", key.value())
                    .query((rs, row) -> storedOrNull(rs.getString("operation"), rs.getString("request_fingerprint"),
                            rs.getString("outcome"), rs.getString("response_payload")))
                    .optional();
            setLockTimeout(previousTimeout);
            // Una fila vigente sin resultado solo existe mientras otra transacción la tiene abierta; si ya hizo
            // commit sin completar es un estado inválido, que se trata como petición en curso.
            return existing.<IdempotencyClaim>map(IdempotencyClaim.Existing::new)
                    .orElseThrow(() -> new IdempotencyInProgressException(retryAfterSeconds));
        } catch (DataAccessException e) {
            if (isLockNotAvailable(e)) {
                throw new IdempotencyInProgressException(retryAfterSeconds);
            }
            throw e;
        }
    }

    @Override
    public void complete(CustomerId customer, IdempotencyKey key, IdempotencyOutcome outcome, String payloadJson) {
        int rows = jdbc.sql(COMPLETE)
                .param("customer", customer.value())
                .param("key", key.value())
                .param("outcome", outcome.name())
                .param("payload", payloadJson)
                .update();
        if (rows != 1) {
            throw new IllegalStateException("idempotency key was not claimed by the current transaction");
        }
    }

    @Override
    public int deleteExpired(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.sql(DELETE_EXPIRED).param("limit", limit).update();
    }

    private void setLockTimeout(String value) {
        jdbc.sql(SET_LOCAL_LOCK_TIMEOUT).param("value", value).query(String.class).single();
    }

    private static StoredIdempotencyRecord storedOrNull(String operation, String fingerprint, String outcome,
                                                        String payload) {
        if (outcome == null || payload == null) {
            return null;
        }
        return new StoredIdempotencyRecord(IdempotentOperation.valueOf(operation),
                new RequestFingerprint(fingerprint.strip()), IdempotencyOutcome.valueOf(outcome), payload);
    }

    private static boolean isLockNotAvailable(DataAccessException e) {
        if (e instanceof CannotAcquireLockException) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
