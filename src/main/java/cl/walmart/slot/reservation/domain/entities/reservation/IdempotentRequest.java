package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import java.util.Objects;

/** Identidad de una petición idempotente: alcance (cliente + clave), operación y huella de sus campos. */
public record IdempotentRequest(CustomerId customer, IdempotencyKey key, IdempotentOperation operation,
                                RequestFingerprint fingerprint) {

    public IdempotentRequest {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
    }
}
