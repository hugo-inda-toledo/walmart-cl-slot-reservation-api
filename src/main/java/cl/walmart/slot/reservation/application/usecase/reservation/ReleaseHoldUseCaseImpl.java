package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseHoldUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional de la liberación. La transición {@code ACTIVE -> RELEASED} es una guarda atómica: solo el
 * ganador devuelve el cupo, así N liberaciones simultáneas lo devuelven exactamente una vez. No usa clave de
 * idempotencia: es idempotente por naturaleza.
 */
@Service
@Transactional(noRollbackFor = ReservationException.class)
class ReleaseHoldUseCaseImpl implements ReleaseHoldUseCase {

    private final CurrentCustomerProvider customers;
    private final HoldRepository holds;
    private final SlotCapacityService capacity;
    private final ReservationMetrics metrics;
    private final ApplicationEventPublisher events;

    ReleaseHoldUseCaseImpl(CurrentCustomerProvider customers, HoldRepository holds, SlotCapacityService capacity,
                           ReservationMetrics metrics, ApplicationEventPublisher events) {
        this.customers = customers;
        this.holds = holds;
        this.capacity = capacity;
        this.metrics = metrics;
        this.events = events;
    }

    @Override
    public void release(HoldId holdId) {
        Objects.requireNonNull(holdId, "holdId must not be null");
        var customer = customers.currentCustomer();
        var released = holds.markReleased(holdId, customer);
        if (released.isPresent()) {
            giveBackSlot(released.get());
            return;
        }
        try {
            checkAlreadyClosed(holdId, customer);
        } catch (ReservationException rejection) {
            metrics.holdRejected(rejection.reservationCode().value());
            throw rejection;
        }
    }

    private void giveBackSlot(SlotHold hold) {
        var zoneDays = capacity.releaseHeld(Map.of(hold.slot().slotId(), 1));
        events.publishEvent(new ReservationLifecycleEvent(ReservationLifecycleEvent.Kind.HOLD_RELEASED, 1,
                zoneDays));
    }

    /** La guarda falló sin escribir nada: liberada o vencida es éxito sin cambios; el resto se rechaza. */
    private void checkAlreadyClosed(HoldId holdId, CustomerId customer) {
        HoldRecord record = holds.findForCustomer(holdId, customer)
                .orElseThrow(() -> ReservationException.holdNotFound(holdId));
        var hold = record.hold();
        switch (hold.status()) {
            case RELEASED, EXPIRED -> { }
            case CONFIRMED -> throw ReservationException.holdNotActive(hold.id(), hold.status(),
                    record.reservationId().orElse(null));
            case ACTIVE -> throw new IllegalStateException("La retención " + holdId.value()
                    + " figura ACTIVE aunque la liberación guardada no la afectó");
        }
    }
}
