package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;

/** Consulta una reserva del cliente autenticado; solo lectura. */
public interface GetReservationUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code RESERVATION_NOT_FOUND}
     *         si no existe o es ajena
     */
    ReservationView get(ReservationId reservationId);
}
