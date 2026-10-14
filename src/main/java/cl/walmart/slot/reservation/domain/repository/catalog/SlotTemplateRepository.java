package cl.walmart.slot.reservation.domain.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplate;
import java.util.List;

/**
 * Lectura de las plantillas de ventana configuradas.
 */
public interface SlotTemplateRepository {

    /** Plantillas activas de todas las zonas. */
    List<SlotTemplate> findAllActive();
}
