package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import java.util.Objects;

/**
 * Decisión de un resolver de cobertura: pasar a la siguiente regla o resolver la dirección.
 */
public sealed interface ResolverDecision {

    /** La regla no aplica; continúa la cadena. */
    record Pass() implements ResolverDecision {
    }

    /** El punto cae en un área de exclusión activa: sin cobertura. */
    record Excluded() implements ResolverDecision {
    }

    /** El punto cae en un área de inclusión de una zona activa. */
    record ZoneMatched(DeliveryZone zone, Comuna comuna) implements ResolverDecision {
        public ZoneMatched {
            Objects.requireNonNull(zone, "zone must not be null");
            Objects.requireNonNull(comuna, "comuna must not be null");
        }
    }

    /** La comuna quedó determinada por el método indicado; la zona se asigna después. */
    record ComunaMatched(Comuna comuna, ResolutionMethod method) implements ResolverDecision {
        public ComunaMatched {
            Objects.requireNonNull(comuna, "comuna must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }
}
