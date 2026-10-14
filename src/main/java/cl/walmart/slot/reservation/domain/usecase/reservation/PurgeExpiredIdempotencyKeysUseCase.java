package cl.walmart.slot.reservation.domain.usecase.reservation;

/** Purga las claves de idempotencia vencidas; un lote por invocación. */
public interface PurgeExpiredIdempotencyKeysUseCase {

    /**
     * Borra hasta {@code purgeBatchSize} claves vencidas en una sola transacción. El llamador repite la
     * invocación mientras el lote venga lleno.
     *
     * @return cantidad de claves borradas
     */
    int purgeExpired();
}
