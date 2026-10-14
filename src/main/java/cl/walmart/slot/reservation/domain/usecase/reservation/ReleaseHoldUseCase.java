package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;

/** Libera una retención del cliente autenticado; idempotente por naturaleza (guarda de estado). */
public interface ReleaseHoldUseCase {

    /**
     * Devuelve el cupo si la retención estaba activa; si ya estaba liberada o vencida no cambia nada.
     *
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code HOLD_NOT_FOUND} si no
     *         existe o es ajena; {@code HOLD_NOT_ACTIVE} si ya fue confirmada
     */
    void release(HoldId holdId);
}
