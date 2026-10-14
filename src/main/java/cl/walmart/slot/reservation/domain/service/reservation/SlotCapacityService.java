package cl.walmart.slot.reservation.domain.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Map;
import java.util.Set;

/**
 * Única puerta de escritura de los contadores {@code reserved}/{@code held}. Siempre dentro de la transacción del
 * caso de uso; no publica eventos (los publican los casos de uso con su {@code Kind}).
 */
public interface SlotCapacityService {

    HoldCapacityOutcome tryHold(SlotId slot);

    /** @throws IllegalStateException si el ajuste no afectó la fila esperada */
    ZoneDay moveHeldToReserved(SlotId slot);

    /** @throws IllegalStateException si se tocaron menos filas que ventanas pedidas */
    Set<ZoneDay> releaseHeld(Map<SlotId, Integer> counts);

    /** @throws IllegalStateException si el ajuste no afectó la fila esperada */
    ZoneDay releaseReserved(SlotId slot);
}
