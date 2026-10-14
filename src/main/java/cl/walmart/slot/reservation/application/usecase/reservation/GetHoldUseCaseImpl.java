package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.helper.reservation.ReservationViews;
import cl.walmart.slot.reservation.domain.usecase.reservation.GetHoldUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de una retención. Usa el repositorio directamente (sin servicio intermedio: sería un pass-through) y
 * informa el estado efectivo con la hora de la base de datos, aunque el barredor aún no procese la retención.
 */
@Service
@Transactional(readOnly = true)
class GetHoldUseCaseImpl implements GetHoldUseCase {

    private final CurrentCustomerProvider customers;
    private final HoldRepository holds;

    GetHoldUseCaseImpl(CurrentCustomerProvider customers, HoldRepository holds) {
        this.customers = customers;
        this.holds = holds;
    }

    @Override
    public HoldView get(HoldId holdId) {
        Objects.requireNonNull(holdId, "holdId must not be null");
        HoldRecord record = holds.findForCustomer(holdId, customers.currentCustomer())
                .orElseThrow(() -> ReservationException.holdNotFound(holdId));
        var hold = record.hold();
        return ReservationViews.of(hold, hold.effectiveStatus(record.dbNow()), record.reservationId().orElse(null));
    }
}
