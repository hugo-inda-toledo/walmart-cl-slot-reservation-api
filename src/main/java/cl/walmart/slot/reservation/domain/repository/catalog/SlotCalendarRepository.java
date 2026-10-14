package cl.walmart.slot.reservation.domain.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.NewDeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotCalendarInsertion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Escritura del calendario de ventanas. Nunca modifica los contadores {@code reserved} ni {@code held}.
 */
public interface SlotCalendarRepository {

    /**
     * Inserta solo las ventanas que no existen (idempotente y seguro bajo concurrencia); no actualiza filas
     * existentes.
     *
     * @return las zona-fechas en las que se creó al menos una ventana nueva
     */
    SlotCalendarInsertion insertMissing(List<NewDeliverySlot> slots);

    /**
     * Cierra la capacidad restante de las ventanas de la zona y fecha cuyo cierre es posterior a {@code now}
     * (deja {@code capacity = reserved + held}); es idempotente.
     *
     * @return cantidad de ventanas modificadas
     */
    int closeRemainingCapacity(CatalogZoneId zone, LocalDate date, Instant now);
}
