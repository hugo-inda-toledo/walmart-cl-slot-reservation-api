package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import java.time.LocalDate;
import java.util.List;

/** Lectura read-through por zona y fecha; los estados se calculan fuera de este servicio. */
public interface AvailabilityService {
    List<ZoneDayAvailability> load(CatalogZoneId zone, List<LocalDate> dates);
}
