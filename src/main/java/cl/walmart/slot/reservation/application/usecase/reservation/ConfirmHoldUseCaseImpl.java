package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.helper.reservation.ReservationViews;
import cl.walmart.slot.reservation.domain.entities.reservation.ConfirmHoldCommand;
import cl.walmart.slot.reservation.domain.usecase.reservation.ConfirmHoldUseCase;
import cl.walmart.slot.reservation.domain.service.reservation.IdempotencyService;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional e idempotencia de la confirmación. Los rechazos de negocio ({@link ReservationException})
 * hacen commit para fijarse en la clave; el conflicto por carrera ({@link ReservationConflictException}) y los
 * errores internos revierten todo, incluida la transición de la retención.
 */
@Service
@Transactional(noRollbackFor = ReservationException.class)
class ConfirmHoldUseCaseImpl implements ConfirmHoldUseCase {

    private final CurrentCustomerProvider customers;
    private final IdempotencyService idempotency;
    private final HoldRepository holds;
    private final ReservationRepository reservations;
    private final SlotCapacityService capacity;
    private final ReservationMetrics metrics;
    private final ApplicationEventPublisher events;

    ConfirmHoldUseCaseImpl(CurrentCustomerProvider customers, IdempotencyService idempotency, HoldRepository holds,
                           ReservationRepository reservations, SlotCapacityService capacity,
                           ReservationMetrics metrics, ApplicationEventPublisher events) {
        this.customers = customers;
        this.idempotency = idempotency;
        this.holds = holds;
        this.reservations = reservations;
        this.capacity = capacity;
        this.metrics = metrics;
        this.events = events;
    }

    @Override
    public ReservationView confirm(ConfirmHoldCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var customer = customers.currentCustomer();
        var request = new IdempotentRequest(customer, command.key(), IdempotentOperation.CONFIRM_HOLD,
                RequestFingerprint.of(IdempotentOperation.CONFIRM_HOLD, command.holdId().value().toString()));
        try {
            return idempotency.execute(request, ReservationView.class, () -> confirmHold(customer, command));
        } catch (ReservationException rejection) {
            metrics.holdRejected(rejection.reservationCode().value());
            throw rejection;
        } catch (ReservationConflictException conflict) {
            metrics.holdRejected(conflict.reservationCode().value());
            throw conflict;
        }
    }

    private ReservationView confirmHold(CustomerId customer, ConfirmHoldCommand command) {
        var confirmedHold = holds.markConfirmed(command.holdId(), customer)
                .orElseThrow(() -> rejectionFor(customer, command));
        Reservation reservation = reservations.insertConfirmed(ReservationId.random(), confirmedHold)
                .orElseThrow(() -> ReservationConflictException.orderAlreadyReserved(null));
        var zoneDay = capacity.moveHeldToReserved(confirmedHold.slot().slotId());
        events.publishEvent(new ReservationLifecycleEvent(ReservationLifecycleEvent.Kind.HOLD_CONFIRMED, 1,
                Set.of(zoneDay)));
        return ReservationViews.of(reservation);
    }

    /** La guarda falló sin escribir nada: distingue la causa leyendo la retención del cliente. */
    private ReservationException rejectionFor(CustomerId customer, ConfirmHoldCommand command) {
        var record = holds.findForCustomer(command.holdId(), customer);
        if (record.isEmpty()) {
            return ReservationException.holdNotFound(command.holdId());
        }
        HoldRecord found = record.get();
        var hold = found.hold();
        // ACTIVE con vencimiento alcanzado o ya EXPIRED: la retención venció; CONFIRMED/RELEASED: no está activa.
        if (hold.status() == HoldStatus.ACTIVE || hold.status() == HoldStatus.EXPIRED) {
            return ReservationException.holdExpired(hold.id(), hold.expiresAt());
        }
        return ReservationException.holdNotActive(hold.id(), hold.status(), found.reservationId().orElse(null));
    }
}
