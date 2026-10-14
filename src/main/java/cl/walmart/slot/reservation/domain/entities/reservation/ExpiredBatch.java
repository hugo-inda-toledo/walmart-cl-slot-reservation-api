package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Objects;
import java.util.Set;

/** Resultado de expirar un lote de retenciones vencidas: cantidad y zona-fechas cuya disponibilidad cambió. */
public record ExpiredBatch(int count, Set<ZoneDay> zoneDays) {

    public ExpiredBatch {
        Objects.requireNonNull(zoneDays, "zoneDays must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        zoneDays = Set.copyOf(zoneDays);
    }

    public static ExpiredBatch empty() {
        return new ExpiredBatch(0, Set.of());
    }
}
