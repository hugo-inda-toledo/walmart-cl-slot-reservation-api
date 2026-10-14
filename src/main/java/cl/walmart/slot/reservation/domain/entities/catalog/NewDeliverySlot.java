package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Ventana a insertar en el calendario: instantes de inicio, término y cierre, con el costo y la capacidad vigentes
 * de la plantilla al generarla. Los contadores nacen en cero y los gestiona la base de datos.
 */
public record NewDeliverySlot(
        SlotTemplateId templateId,
        CatalogZoneId zoneId,
        LocalDate deliveryDate,
        ServiceType serviceType,
        Instant startAt,
        Instant endAt,
        Instant cutoffAt,
        Money price,
        Capacity capacity) {

    public NewDeliverySlot {
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(deliveryDate, "deliveryDate must not be null");
        Objects.requireNonNull(serviceType, "serviceType must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
        Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(capacity, "capacity must not be null");
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must be before endAt: " + startAt + " - " + endAt);
        }
        if (cutoffAt.isAfter(startAt)) {
            throw new IllegalArgumentException("cutoffAt must not be after startAt: " + cutoffAt + " > " + startAt);
        }
    }
}
