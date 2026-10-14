package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ExpiredBatch;
import cl.walmart.slot.reservation.domain.service.reservation.HoldService;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quien transiciona la retención es quien devuelve el cupo: la transición y el ajuste de contadores ocurren en la
 * misma transacción del llamador, por lo que cada cupo se devuelve exactamente una vez.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ReservationException.class)
class HoldServiceImpl implements HoldService {

    private final HoldRepository holds;
    private final SlotCapacityService capacity;
    private final ApplicationEventPublisher events;

    HoldServiceImpl(HoldRepository holds, SlotCapacityService capacity, ApplicationEventPublisher events) {
        this.holds = holds;
        this.capacity = capacity;
        this.events = events;
    }

    @Override
    public Optional<ZoneDay> expireStaleForOrder(CustomerId customer, OrderId order) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(order, "order must not be null");
        var batch = release(holds.expireStaleForOrder(customer, order).stream().toList());
        return batch.zoneDays().stream().findFirst();
    }

    @Override
    public ExpiredBatch expireDue(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return release(holds.expireDueBatch(limit));
    }

    private ExpiredBatch release(List<SlotHold> expired) {
        if (expired.isEmpty()) {
            return ExpiredBatch.empty();
        }
        var countsBySlot = new HashMap<SlotId, Integer>();
        expired.forEach(hold -> countsBySlot.merge(hold.slot().slotId(), 1, Integer::sum));
        Set<ZoneDay> zoneDays = capacity.releaseHeld(countsBySlot);
        events.publishEvent(new ReservationLifecycleEvent(
                ReservationLifecycleEvent.Kind.HOLD_EXPIRED, expired.size(), zoneDays));
        return new ExpiredBatch(expired.size(), zoneDays);
    }
}
