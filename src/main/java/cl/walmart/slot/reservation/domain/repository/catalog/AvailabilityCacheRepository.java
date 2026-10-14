package cl.walmart.slot.reservation.domain.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Caché de disponibilidad por zona-día. Es solo un acelerador: la base de datos es la fuente de verdad.
 *
 * <p>Contrato: ningún método lanza excepciones. Una falla de la caché se trata como acierto vacío ({@code miss})
 * en lecturas y se ignora en escrituras, de modo que las respuestas no cambian si la caché cae.
 */
public interface AvailabilityCacheRepository {

    /** Zona-días presentes en caché para las fechas pedidas; las ausentes (o ante falla) no figuran. */
    Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId zone, Collection<LocalDate> dates);

    /** Guarda zona-días; ante falla no hace nada. */
    void saveAll(Collection<ZoneDayAvailability> days);

    /** Elimina la zona-fecha de la caché; ante falla no hace nada. */
    void evict(ZoneDate zoneDate);
}
