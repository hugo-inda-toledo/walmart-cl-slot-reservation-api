package cl.walmart.slot.reservation.domain.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Persistencia de retenciones. Toda consulta y guarda filtra por cliente: un recurso ajeno no existe. */
public interface HoldRepository {

    /**
     * Inserta una retención activa con {@code created_at = now()} y {@code expires_at = now() + ttl} de la base de
     * datos; vacío si otra retención activa de la misma orden ya existe.
     */
    Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order, SlotSnapshot slot, Duration ttl);

    Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer);

    Optional<HoldRecord> findActiveForOrder(CustomerId customer, OrderId order);

    /** {@code ACTIVE -> EXPIRED} de la retención vencida de la orden; vacío si no hay una vencida. */
    Optional<SlotHold> expireStaleForOrder(CustomerId customer, OrderId order);

    /** {@code ACTIVE -> CONFIRMED} con {@code expires_at > now()}; vacío si la guarda falla. */
    Optional<SlotHold> markConfirmed(HoldId id, CustomerId customer);

    /** {@code ACTIVE -> RELEASED}; vacío si la guarda falla. */
    Optional<SlotHold> markReleased(HoldId id, CustomerId customer);

    /** Expira hasta {@code limit} retenciones vencidas (más antiguas primero) con {@code FOR UPDATE SKIP LOCKED}. */
    List<SlotHold> expireDueBatch(int limit);
}
