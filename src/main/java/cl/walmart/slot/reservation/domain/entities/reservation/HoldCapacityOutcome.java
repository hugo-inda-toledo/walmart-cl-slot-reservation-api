package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.util.Objects;

/** Resultado de intentar tomar un cupo de una ventana. */
public sealed interface HoldCapacityOutcome {

    /** Cupo tomado; {@code zoneDay} es la zona-fecha cuya disponibilidad cambió. */
    record Acquired(ZoneDay zoneDay) implements HoldCapacityOutcome {
        public Acquired {
            Objects.requireNonNull(zoneDay, "zoneDay must not be null");
        }
    }

    /** Sin cupos; {@code dbNow} es la hora de la base de datos de la misma decisión. */
    record SoldOut(Instant dbNow) implements HoldCapacityOutcome {
        public SoldOut {
            Objects.requireNonNull(dbNow, "dbNow must not be null");
        }
    }

    /** La ventana ya cerró según la hora de la base de datos. */
    record CutoffPassed(Instant cutoffAt) implements HoldCapacityOutcome {
        public CutoffPassed {
            Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        }
    }

    /** La ventana no existe. */
    record NotFound() implements HoldCapacityOutcome {
    }
}
