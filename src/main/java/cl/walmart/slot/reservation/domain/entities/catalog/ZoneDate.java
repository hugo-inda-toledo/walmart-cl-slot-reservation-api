package cl.walmart.slot.reservation.domain.entities.catalog;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Par zona-fecha: unidad de generación del calendario, de caché y de invalidación.
 */
public record ZoneDate(CatalogZoneId zoneId, LocalDate date) {

    public ZoneDate {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(date, "date must not be null");
    }
}
