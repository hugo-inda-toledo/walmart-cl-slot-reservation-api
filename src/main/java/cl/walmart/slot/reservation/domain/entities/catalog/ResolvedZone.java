package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import java.util.Objects;

/** Zona y comuna resueltas por geo, en la representación que consumen las consultas del catálogo. */
public record ResolvedZone(CatalogZoneId zoneId, String zoneCode, String zoneName, String comunaCode,
                           String comunaName) {
    public ResolvedZone {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(zoneCode, "zoneCode must not be null");
        Objects.requireNonNull(zoneName, "zoneName must not be null");
        Objects.requireNonNull(comunaCode, "comunaCode must not be null");
        Objects.requireNonNull(comunaName, "comunaName must not be null");
    }
}
