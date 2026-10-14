package cl.walmart.slot.reservation.domain.port.geo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resultado de resolver una dirección a una zona de despacho.
 */
public sealed interface ZoneResolution {

    /** Regla que determinó la comuna. */
    enum Method { ZONE_AREA, COMUNA_BOUNDARY, NEAREST_COMUNA, COMUNA_CODE }

    /** Motivo de no cobertura. */
    enum Reason { EXCLUDED_AREA, OUTSIDE_SERVICE_AREA, COMUNA_NOT_SERVED, ZONE_INACTIVE }

    record Covered(UUID zoneId, String zoneCode, String zoneName, String comunaCode, String comunaName,
                   Method method, boolean comunaDiffersFromDeclared) implements ZoneResolution {
        public Covered {
            Objects.requireNonNull(zoneId, "zoneId must not be null");
            Objects.requireNonNull(zoneCode, "zoneCode must not be null");
            Objects.requireNonNull(zoneName, "zoneName must not be null");
            Objects.requireNonNull(comunaCode, "comunaCode must not be null");
            Objects.requireNonNull(comunaName, "comunaName must not be null");
            Objects.requireNonNull(method, "method must not be null");
        }
    }

    record NotCovered(Reason reason, Optional<String> comunaCode) implements ZoneResolution {
        public NotCovered {
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(comunaCode, "comunaCode must not be null");
        }
    }
}
