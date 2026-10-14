package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.util.Objects;
import java.util.Set;

/** Cambio de ciclo de vida publicado en la transacción y consumido after-commit (invalidación y métricas). */
public record ReservationLifecycleEvent(Kind kind, int count, Set<ZoneDay> zoneDays) {

    public enum Kind {
        HOLD_CREATED,
        HOLD_CONFIRMED,
        HOLD_RELEASED,
        HOLD_EXPIRED,
        RESERVATION_CANCELLED
    }

    public ReservationLifecycleEvent {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(zoneDays, "zoneDays must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        zoneDays = Set.copyOf(zoneDays);
    }
}
