package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import cl.walmart.slot.reservation.application.helper.reservation.ReservationViews;
import cl.walmart.slot.reservation.domain.service.reservation.AlternativeSlotsFinder;
import cl.walmart.slot.reservation.domain.service.reservation.HoldService;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldSlotCommand;
import cl.walmart.slot.reservation.domain.usecase.reservation.HoldSlotUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.service.reservation.IdempotencyService;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import cl.walmart.slot.reservation.domain.entities.reservation.RequestFingerprint;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límite transaccional y de idempotencia de la retención. Los rechazos de negocio ({@link ReservationException})
 * hacen commit para fijarse en la clave; los conflictos por carrera ({@link ReservationConflictException}) y los
 * errores internos revierten todo, incluido el {@code held + 1}.
 */
@Service
@Transactional(noRollbackFor = ReservationException.class)
class HoldSlotUseCaseImpl implements HoldSlotUseCase {

    private final CurrentCustomerProvider customers;
    private final IdempotencyService idempotency;
    private final SlotQueryPort catalog;
    private final ReservationRepository reservations;
    private final HoldRepository holds;
    private final HoldService holdService;
    private final SlotCapacityService capacity;
    private final AlternativeSlotsFinder alternatives;
    private final ReservationSettings settings;
    private final ReservationMetrics metrics;
    private final ApplicationEventPublisher events;

    HoldSlotUseCaseImpl(CurrentCustomerProvider customers, IdempotencyService idempotency, SlotQueryPort catalog,
                        ReservationRepository reservations, HoldRepository holds, HoldService holdService,
                        SlotCapacityService capacity, AlternativeSlotsFinder alternatives,
                        ReservationSettings settings, ReservationMetrics metrics,
                        ApplicationEventPublisher events) {
        this.customers = customers;
        this.idempotency = idempotency;
        this.catalog = catalog;
        this.reservations = reservations;
        this.holds = holds;
        this.holdService = holdService;
        this.capacity = capacity;
        this.alternatives = alternatives;
        this.settings = settings;
        this.metrics = metrics;
        this.events = events;
    }

    @Override
    public HoldView hold(HoldSlotCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var customer = customers.currentCustomer();
        var request = new IdempotentRequest(customer, command.key(), IdempotentOperation.HOLD_SLOT,
                RequestFingerprint.of(IdempotentOperation.HOLD_SLOT,
                        command.slotId().value().toString(), command.orderId().value()));
        try {
            return idempotency.execute(request, HoldView.class, () -> createHold(customer, command));
        } catch (ReservationException rejection) {
            metrics.holdRejected(rejection.reservationCode().value());
            throw rejection;
        } catch (ReservationConflictException conflict) {
            metrics.holdRejected(conflict.reservationCode().value());
            throw conflict;
        }
    }

    /** Orden de validación del diseño; ninguna escritura de efectos precede a un rechazo fijable (salvo 2.10). */
    private HoldView createHold(CustomerId customer, HoldSlotCommand command) {
        var slotId = command.slotId();
        var slot = catalog.findSlot(slotId.value()).orElseThrow(() -> ReservationException.slotNotFound(slotId));
        if (slot.status() == SlotView.Status.NO_SERVICE) {
            throw ReservationException.slotNoService(slotId, slot.deliveryDate());
        }
        reservations.findConfirmedForOrder(customer, command.orderId()).ifPresent(reservation -> {
            throw ReservationException.orderAlreadyReserved(reservation.id());
        });
        releaseStaleHoldOrFailIfActive(customer, command);

        return switch (capacity.tryHold(slotId)) {
            case HoldCapacityOutcome.Acquired acquired -> insertAndPublish(customer, command, slot, acquired);
            case HoldCapacityOutcome.SoldOut soldOut ->
                    throw ReservationException.slotFull(slotId, alternatives.find(slot, soldOut.dbNow()));
            case HoldCapacityOutcome.CutoffPassed closed ->
                    throw ReservationException.slotCutoffPassed(slotId, closed.cutoffAt());
            case HoldCapacityOutcome.NotFound ignored -> throw ReservationException.slotNotFound(slotId);
        };
    }

    private void releaseStaleHoldOrFailIfActive(CustomerId customer, HoldSlotCommand command) {
        var existing = holds.findActiveForOrder(customer, command.orderId());
        if (existing.isEmpty()) {
            return;
        }
        HoldRecord record = existing.get();
        if (record.hold().effectiveStatus(record.dbNow()) == HoldStatus.EXPIRED) {
            holdService.expireStaleForOrder(customer, command.orderId());
            return;
        }
        throw ReservationException.activeHoldExists(record.hold().id());
    }

    private HoldView insertAndPublish(CustomerId customer, HoldSlotCommand command, SlotView slot,
                                      HoldCapacityOutcome.Acquired acquired) {
        var snapshot = new SlotSnapshot(new SlotId(slot.slotId()), slot.zoneId(), slot.deliveryDate(),
                slot.startAt(), slot.endAt(), slot.cutoffAt(), slot.priceClp());
        SlotHold hold = holds.insertActive(HoldId.random(), customer, command.orderId(), snapshot,
                        settings.holdTtl())
                .orElseThrow(() -> ReservationConflictException.activeHoldExists(null));
        events.publishEvent(new ReservationLifecycleEvent(ReservationLifecycleEvent.Kind.HOLD_CREATED, 1,
                Set.of(acquired.zoneDay())));
        return ReservationViews.of(hold, hold.status(), null);
    }
}
