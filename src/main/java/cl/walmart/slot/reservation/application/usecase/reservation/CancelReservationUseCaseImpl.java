package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.helper.reservation.ReservationViews;
import cl.walmart.slot.reservation.domain.usecase.reservation.CancelReservationUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional de la cancelación. La transición {@code CONFIRMED -> CANCELLED} con
 * {@code cutoff_at > now()} es una guarda atómica: solo el ganador devuelve el cupo reservado. No usa clave de
 * idempotencia: es idempotente por naturaleza.
 */
@Service
@Transactional(noRollbackFor = ReservationException.class)
class CancelReservationUseCaseImpl implements CancelReservationUseCase {

    private final CurrentCustomerProvider customers;
    private final ReservationRepository reservations;
    private final SlotCapacityService capacity;
    private final ReservationMetrics metrics;
    private final ApplicationEventPublisher events;

    CancelReservationUseCaseImpl(CurrentCustomerProvider customers, ReservationRepository reservations,
                                 SlotCapacityService capacity, ReservationMetrics metrics,
                                 ApplicationEventPublisher events) {
        this.customers = customers;
        this.reservations = reservations;
        this.capacity = capacity;
        this.metrics = metrics;
        this.events = events;
    }

    @Override
    public ReservationView cancel(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        var customer = customers.currentCustomer();
        var cancelled = reservations.markCancelled(reservationId, customer);
        if (cancelled.isPresent()) {
            var zoneDay = capacity.releaseReserved(cancelled.get().slot().slotId());
            events.publishEvent(new ReservationLifecycleEvent(
                    ReservationLifecycleEvent.Kind.RESERVATION_CANCELLED, 1, Set.of(zoneDay)));
            return ReservationViews.of(cancelled.get());
        }
        try {
            return viewOfAlreadyCancelled(reservationId, customer);
        } catch (ReservationException rejection) {
            metrics.holdRejected(rejection.reservationCode().value());
            throw rejection;
        }
    }

    /** La guarda falló sin escribir nada: ya cancelada devuelve su vista; cierre alcanzado o ausente se rechaza. */
    private ReservationView viewOfAlreadyCancelled(ReservationId reservationId, CustomerId customer) {
        ReservationRecord record = reservations.findForCustomer(reservationId, customer)
                .orElseThrow(() -> ReservationException.reservationNotFound(reservationId));
        Reservation reservation = record.reservation();
        return switch (reservation.status()) {
            case CANCELLED -> ReservationViews.of(reservation);
            case CONFIRMED -> {
                var cutoffAt = reservation.slot().cutoffAt();
                if (!cutoffAt.isAfter(record.dbNow())) {
                    throw ReservationException.reservationCancellationClosed(reservationId, cutoffAt);
                }
                throw new IllegalStateException("La reserva " + reservationId.value()
                        + " sigue CONFIRMED antes de su cierre aunque la cancelación guardada no la afectó");
            }
        };
    }
}
