package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.helper.reservation.ReservationViews;
import cl.walmart.slot.reservation.domain.usecase.reservation.GetReservationUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de una reserva. Usa el repositorio directamente (sin servicio intermedio: sería un pass-through). */
@Service
@Transactional(readOnly = true)
class GetReservationUseCaseImpl implements GetReservationUseCase {

    private final CurrentCustomerProvider customers;
    private final ReservationRepository reservations;

    GetReservationUseCaseImpl(CurrentCustomerProvider customers, ReservationRepository reservations) {
        this.customers = customers;
        this.reservations = reservations;
    }

    @Override
    public ReservationView get(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return reservations.findForCustomer(reservationId, customers.currentCustomer())
                .map(record -> ReservationViews.of(record.reservation()))
                .orElseThrow(() -> ReservationException.reservationNotFound(reservationId));
    }
}
