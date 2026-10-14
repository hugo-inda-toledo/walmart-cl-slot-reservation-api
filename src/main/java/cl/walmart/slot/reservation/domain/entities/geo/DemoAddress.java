package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * Dirección de demostración con el resultado de cobertura esperado.
 */
public record DemoAddress(String id, String label, String street, String number, ComunaCode comunaCode,
                          String comunaName, Optional<GeoPoint> point, ExpectedCoverage expected) {

    public DemoAddress {
        requireText(id, "id");
        requireText(label, "label");
        requireText(street, "street");
        requireText(number, "number");
        requireText(comunaName, "comunaName");
        Objects.requireNonNull(comunaCode, "comunaCode must not be null");
        Objects.requireNonNull(point, "point must not be null");
        Objects.requireNonNull(expected, "expected must not be null");
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    /**
     * Resultado esperado: cubierta ⇒ zona y método presentes, sin motivo; no cubierta ⇒ motivo presente,
     * sin zona ni método.
     */
    public record ExpectedCoverage(boolean covered, Optional<String> zoneCode, Optional<ResolutionMethod> method,
                                   Optional<NotCoveredReason> reason) {

        public ExpectedCoverage {
            Objects.requireNonNull(zoneCode, "zoneCode must not be null");
            Objects.requireNonNull(method, "method must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (covered && (zoneCode.isEmpty() || method.isEmpty() || reason.isPresent())) {
                throw new IllegalArgumentException("covered expectation requires zoneCode and method, no reason");
            }
            if (!covered && (reason.isEmpty() || zoneCode.isPresent() || method.isPresent())) {
                throw new IllegalArgumentException("not covered expectation requires reason only");
            }
        }
    }
}
