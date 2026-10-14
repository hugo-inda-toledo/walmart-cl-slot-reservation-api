package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;

/** Consulta una retención del cliente autenticado; solo lectura. */
public interface GetHoldUseCase {

    /**
     * Devuelve la retención con su estado efectivo según la hora de la base de datos y, si fue confirmada, la
     * reserva resultante.
     *
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code HOLD_NOT_FOUND} si no
     *         existe o es ajena
     */
    HoldView get(HoldId holdId);
}
