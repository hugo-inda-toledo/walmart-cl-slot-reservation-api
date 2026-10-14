package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplate;
import java.time.Instant;

/**
 * Política que decide el instante de cierre de una ventana (punto de extensión abierto a nuevas reglas).
 */
public interface CutoffPolicy {

    /** Instante de cierre propuesto para la ventana de la plantilla que empieza en {@code startAt}. */
    Instant cutoffFor(SlotTemplate template, Instant startAt);
}
