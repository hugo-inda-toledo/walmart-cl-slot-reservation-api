package cl.walmart.slot.reservation.domain.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldSlotCommand;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;

/** Retiene una ventana de despacho para una orden del cliente autenticado, de forma idempotente. */
public interface HoldSlotUseCase {

    /**
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException rechazo de negocio (ventana
     *         inexistente, sin servicio, cerrada o agotada; orden reservada o con retención activa; clave reusada)
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException carrera entre claves
     *         distintas de la misma orden: se revierte todo y el reintento llega al mismo código por la vía previa
     */
    HoldView hold(HoldSlotCommand command);
}
