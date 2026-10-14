package cl.walmart.slot.reservation.domain.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ExpiredBatch;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Optional;

/** Expiración de retenciones, en la transacción del llamador. */
public interface HoldService {

    /** Expira la retención activa vencida de la orden y devuelve su cupo; vacío si no hay una vencida. */
    Optional<ZoneDay> expireStaleForOrder(CustomerId customer, OrderId order);

    /** Expira hasta {@code limit} retenciones vencidas y devuelve sus cupos agrupados por ventana. */
    ExpiredBatch expireDue(int limit);
}
