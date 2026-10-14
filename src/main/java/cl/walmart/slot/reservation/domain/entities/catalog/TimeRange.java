package cl.walmart.slot.reservation.domain.entities.catalog;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Rango horario local dentro de un mismo día, semiabierto [start, end).
 */
public record TimeRange(LocalTime start, LocalTime end) {

    public TimeRange {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end: " + start + " - " + end);
        }
    }

    /** Dos rangos contiguos (uno termina donde empieza el otro) no se solapan. */
    public boolean overlaps(TimeRange other) {
        Objects.requireNonNull(other, "other must not be null");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
