package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Par zona-fecha: unidad de invalidación de la disponibilidad del catálogo. */
public record ZoneDay(UUID zoneId, LocalDate date) {

    public ZoneDay {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(date, "date must not be null");
    }
}
