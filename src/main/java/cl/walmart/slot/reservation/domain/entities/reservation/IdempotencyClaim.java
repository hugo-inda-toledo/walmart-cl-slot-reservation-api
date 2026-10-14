package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Objects;

/** Resultado de reclamar una clave: la reclamó esta transacción o ya existía una vigente. */
public sealed interface IdempotencyClaim {

    record Claimed() implements IdempotencyClaim {
    }

    record Existing(StoredIdempotencyRecord record) implements IdempotencyClaim {
        public Existing {
            Objects.requireNonNull(record, "record must not be null");
        }
    }
}
