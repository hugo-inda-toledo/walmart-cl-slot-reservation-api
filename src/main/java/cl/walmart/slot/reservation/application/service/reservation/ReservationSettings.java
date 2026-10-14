package cl.walmart.slot.reservation.application.service.reservation;

import java.time.Duration;
import java.util.Objects;

/** Ajustes de reservas ya validados por la infraestructura. */
public record ReservationSettings(
        Duration holdTtl,
        int alternativesMax,
        Duration idempotencyRetention,
        Duration idempotencyWaitTimeout,
        int idempotencyRetryAfterSeconds,
        int sweeperBatchSize,
        int sweeperMaxBatchesPerRun,
        int purgeBatchSize) {

    public ReservationSettings {
        requirePositive(Objects.requireNonNull(holdTtl, "holdTtl"), "holdTtl");
        requirePositive(Objects.requireNonNull(idempotencyRetention, "idempotencyRetention"), "idempotencyRetention");
        requirePositive(Objects.requireNonNull(idempotencyWaitTimeout, "idempotencyWaitTimeout"),
                "idempotencyWaitTimeout");
        if (alternativesMax < 0) {
            throw new IllegalArgumentException("alternativesMax must not be negative");
        }
        if (idempotencyRetryAfterSeconds < 1 || sweeperBatchSize < 1 || sweeperMaxBatchesPerRun < 1
                || purgeBatchSize < 1) {
            throw new IllegalArgumentException("retry-after seconds and batch sizes must be positive");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
