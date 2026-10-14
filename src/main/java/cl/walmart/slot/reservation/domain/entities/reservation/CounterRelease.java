package cl.walmart.slot.reservation.domain.entities.reservation;

import java.util.Set;

/** Resultado de devolver cupos retenidos: zona-fechas afectadas y cantidad de ventanas (filas) tocadas. */
public record CounterRelease(Set<ZoneDay> zoneDays, int rowsTouched) {

    public CounterRelease {
        zoneDays = Set.copyOf(zoneDays);
        if (rowsTouched < 0) {
            throw new IllegalArgumentException("rowsTouched must not be negative");
        }
    }
}
