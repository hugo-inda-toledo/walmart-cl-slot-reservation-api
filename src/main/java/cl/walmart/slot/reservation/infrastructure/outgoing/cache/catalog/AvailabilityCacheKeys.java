package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import java.time.LocalDate;

final class AvailabilityCacheKeys {
    private AvailabilityCacheKeys() { }
    static String forZoneDate(CatalogZoneId zone, LocalDate date) {
        return "catalog:availability:v1:" + zone.value().toString().toLowerCase(java.util.Locale.ROOT) + ":" + date;
    }
}
