package cl.walmart.slot.reservation.domain.port.catalog;

import java.time.LocalDate;
import java.util.UUID;

/** Se llama después de confirmar cambios de capacidad. Nunca propaga una falla de caché. */
public interface AvailabilityCacheInvalidationPort {
    void invalidate(UUID zoneId, LocalDate deliveryDate);
}
