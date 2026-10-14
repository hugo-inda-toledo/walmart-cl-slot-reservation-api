package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoverageResultTest {

    private static final ComunaCode CODE = new ComunaCode("13101");
    private static final Comuna COMUNA = new Comuna(CODE, "Santiago", CODE.provinceCode(), "Santiago", true);
    private static final DeliveryZone ZONE = new DeliveryZone(new DeliveryZoneId(UUID.randomUUID()), "RM_CENTRO",
            "Centro", ZoneStatus.ACTIVE, new DispatchNode("CD1", "CD 1"), List.of(COMUNA), 0, 0);

    private static String describe(CoverageResult result) {
        return switch (result) {
            case CoverageResult.Covered c -> "covered:" + c.zone().code();
            case CoverageResult.NotCovered n -> "not-covered:" + n.reason();
        };
    }

    @Test
    void coveredCarriesComunaZoneAndMethod() {
        var result = new CoverageResult.Covered(COMUNA, ZONE, ResolutionMethod.COMUNA_BOUNDARY, false);

        assertThat(describe(result)).isEqualTo("covered:RM_CENTRO");
        assertThat(result.method()).isEqualTo(ResolutionMethod.COMUNA_BOUNDARY);
        assertThat(result.comunaDiffersFromDeclared()).isFalse();
    }

    @Test
    void notCoveredMayOmitComuna() {
        var result = new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty());

        assertThat(describe(result)).isEqualTo("not-covered:OUTSIDE_SERVICE_AREA");
        assertThat(result.comuna()).isEmpty();
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new CoverageResult.Covered(null, ZONE, ResolutionMethod.ZONE_AREA, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverageResult.Covered(COMUNA, null, ResolutionMethod.ZONE_AREA, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverageResult.Covered(COMUNA, ZONE, null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverageResult.NotCovered(null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CoverageResult.NotCovered(NotCoveredReason.ZONE_INACTIVE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
