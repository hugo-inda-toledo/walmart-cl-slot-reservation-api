package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import java.util.Objects;

/** Fila vigente de una clave de idempotencia. */
public record StoredIdempotencyRecord(IdempotentOperation operation, RequestFingerprint fingerprint,
                                      IdempotencyOutcome outcome, String payloadJson) {

    public StoredIdempotencyRecord {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
    }
}
