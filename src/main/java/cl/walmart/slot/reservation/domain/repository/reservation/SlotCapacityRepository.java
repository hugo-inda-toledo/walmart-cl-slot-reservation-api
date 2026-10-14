package cl.walmart.slot.reservation.domain.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CounterRelease;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Map;
import java.util.Optional;

/** Escrituras atómicas de los contadores {@code reserved}/{@code held}: PostgreSQL es la única fuente de verdad. */
public interface SlotCapacityRepository {

    /**
     * {@code UPDATE held + 1} condicionado a {@code reserved + held < capacity} y a que no haya pasado el cierre. Con
     * 0 filas distingue ventana cerrada, agotada o inexistente con la hora de la base de datos.
     */
    HoldCapacityOutcome tryHold(SlotId slot);

    /** {@code held - 1, reserved + 1 WHERE held > 0}; vacío si no afectó filas. */
    Optional<ZoneDay> moveHeldToReserved(SlotId slot);

    /** {@code held - n} por ventana en una sola sentencia; informa zona-fechas y filas tocadas. */
    CounterRelease releaseHeld(Map<SlotId, Integer> countsBySlot);

    /** {@code reserved - 1 WHERE reserved > 0}; vacío si no afectó filas. */
    Optional<ZoneDay> releaseReserved(SlotId slot);
}
