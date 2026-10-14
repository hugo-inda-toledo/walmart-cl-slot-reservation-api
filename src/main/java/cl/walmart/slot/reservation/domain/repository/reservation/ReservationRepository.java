package cl.walmart.slot.reservation.domain.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationRecord;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import java.util.Optional;

/** Persistencia de reservas. Toda consulta y guarda filtra por cliente. */
public interface ReservationRepository {

    /** Inserta la reserva confirmada con {@code confirmed_at = now()}; vacío si la orden ya tiene una confirmada. */
    Optional<Reservation> insertConfirmed(ReservationId id, SlotHold confirmedHold);

    Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer);

    Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order);

    /** {@code CONFIRMED -> CANCELLED} con {@code cutoff_at > now()}; vacío si la guarda falla. */
    Optional<Reservation> markCancelled(ReservationId id, CustomerId customer);
}
