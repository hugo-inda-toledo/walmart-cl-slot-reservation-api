package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;

/** Cancela una reserva del cliente autenticado; idempotente por naturaleza (guarda de estado). */
public interface CancelReservationUseCase {

    /**
     * Devuelve el cupo de reservados y la vista cancelada; si ya estaba cancelada devuelve la misma vista sin
     * cambios.
     *
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code RESERVATION_NOT_FOUND}
     *         si no existe o es ajena; {@code RESERVATION_CANCELLATION_CLOSED} si el cierre ya ocurrió
     */
    ReservationView cancel(ReservationId reservationId);
}
