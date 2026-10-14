package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de resolver la cobertura de una dirección: cubierta por una zona o no cubierta con motivo.
 */
public sealed interface CoverageResult {

    /** La dirección pertenece a una comuna atendida por una zona activa. */
    record Covered(Comuna comuna, DeliveryZone zone, ResolutionMethod method, boolean comunaDiffersFromDeclared)
            implements CoverageResult {

        public Covered {
            Objects.requireNonNull(comuna, "comuna must not be null");
            Objects.requireNonNull(zone, "zone must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }

    /** La dirección no tiene cobertura; {@code comuna} se informa solo si pudo determinarse. */
    record NotCovered(NotCoveredReason reason, Optional<Comuna> comuna) implements CoverageResult {

        public NotCovered {
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(comuna, "comuna must not be null");
        }
    }
}
