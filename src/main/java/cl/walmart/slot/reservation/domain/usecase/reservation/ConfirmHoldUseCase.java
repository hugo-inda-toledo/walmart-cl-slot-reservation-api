package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ConfirmHoldCommand;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;

/** Confirma una retención del cliente autenticado como reserva, de forma idempotente. */
public interface ConfirmHoldUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException rechazo de negocio (retención
     *         inexistente o ajena, vencida o ya no activa; clave reusada)
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException carrera: la orden ya
     *         tiene una reserva confirmada de otra clave; se revierte todo
     */
    ReservationView confirm(ConfirmHoldCommand command);
}
