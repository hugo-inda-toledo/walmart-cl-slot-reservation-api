package cl.walmart.slot.reservation.domain.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyClaim;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotencyOutcome;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import java.time.Duration;

/** Persistencia de claves de idempotencia: solo PostgreSQL, sin dependencia de la caché. */
public interface IdempotencyRepository {

    /**
     * Reclama la clave del cliente (la inserta o recicla una vencida) esperando como máximo {@code wait} si otra
     * transacción la tiene bloqueada; con espera agotada lanza {@link IdempotencyInProgressException} con
     * {@code retryAfterSeconds}. {@code expires_at = now() + retention} con la hora de la base de datos.
     */
    IdempotencyClaim claim(CustomerId customer, IdempotencyKey key, IdempotentOperation operation,
                           RequestFingerprint fingerprint, Duration retention, Duration wait, int retryAfterSeconds);

    /** Fija el resultado de una clave reclamada por la transacción en curso. */
    void complete(CustomerId customer, IdempotencyKey key, IdempotencyOutcome outcome, String payloadJson);

    /** Borra hasta {@code limit} claves vencidas según la hora de la base de datos; devuelve cuántas. */
    int deleteExpired(int limit);
}
