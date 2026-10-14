package cl.walmart.slot.reservation.domain.service.reservation;

import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import java.time.Instant;
import java.util.List;

/** Sugiere ventanas alternativas ante {@code SLOT_FULL}. Solo lectura: no escribe nada. */
public interface AlternativeSlotsFinder {

    /**
     * @param requested ventana agotada que se intentó retener
     * @param dbNow hora de la base de datos; descarta candidatas con {@code cutoffAt <= dbNow}
     * @return hasta {@code alternativesMax} alternativas (vacía si no hay o si el tope es cero)
     */
    List<AlternativeSlot> find(SlotView requested, Instant dbNow);
}
