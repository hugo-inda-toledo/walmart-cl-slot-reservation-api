package cl.walmart.slot.reservation.domain.usecase.reservation;

/** Libera por vencimiento las retenciones que el barredor encuentra vencidas; un lote por invocación. */
public interface ReleaseExpiredHoldsUseCase {

    /**
     * Expira y devuelve el cupo de hasta {@code sweeperBatchSize} retenciones vencidas en una sola transacción.
     * El llamador repite la invocación mientras el lote venga lleno.
     *
     * @return cantidad de retenciones liberadas
     */
    int releaseExpired();
}
