package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.repository.reservation.SlotCapacityRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los ajustes que no afectan la fila esperada son errores de programación o de datos (la guarda de estado ya los
 * descartó): se elevan como {@link IllegalStateException} para revertir la transacción completa.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ReservationException.class)
class SlotCapacityServiceImpl implements SlotCapacityService {

    private final SlotCapacityRepository capacity;

    SlotCapacityServiceImpl(SlotCapacityRepository capacity) {
        this.capacity = capacity;
    }

    @Override
    public HoldCapacityOutcome tryHold(SlotId slot) {
        return capacity.tryHold(Objects.requireNonNull(slot, "slot must not be null"));
    }

    @Override
    public ZoneDay moveHeldToReserved(SlotId slot) {
        Objects.requireNonNull(slot, "slot must not be null");
        return capacity.moveHeldToReserved(slot).orElseThrow(() -> new IllegalStateException(
                "El ajuste held -> reserved no afectó la ventana " + slot.value()));
    }

    @Override
    public Set<ZoneDay> releaseHeld(Map<SlotId, Integer> counts) {
        Objects.requireNonNull(counts, "counts must not be null");
        if (counts.isEmpty()) {
            return Set.of();
        }
        var release = capacity.releaseHeld(Map.copyOf(counts));
        if (release.rowsTouched() < counts.size()) {
            throw new IllegalStateException("La devolución de cupos retenidos tocó " + release.rowsTouched()
                    + " filas para " + counts.size() + " ventanas");
        }
        return release.zoneDays();
    }

    @Override
    public ZoneDay releaseReserved(SlotId slot) {
        Objects.requireNonNull(slot, "slot must not be null");
        return capacity.releaseReserved(slot).orElseThrow(() -> new IllegalStateException(
                "La devolución de cupo reservado no afectó la ventana " + slot.value()));
    }
}
