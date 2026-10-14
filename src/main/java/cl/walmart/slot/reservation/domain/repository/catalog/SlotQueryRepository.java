package cl.walmart.slot.reservation.domain.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Lectura de ventanas de despacho. Siempre refleja la base de datos, fuente de verdad de la capacidad.
 */
public interface SlotQueryRepository {

    /** Ventanas de la zona en {@code [from, to]}, ordenadas por fecha, inicio y término. */
    List<DeliverySlot> findByZoneAndDates(CatalogZoneId zone, LocalDate from, LocalDate to);

    /** Ventana por identificador, o vacío si no existe. */
    Optional<DeliverySlot> findById(DeliverySlotId id);
}
