package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BorderToleranceAndZoneIdTest {

    @Test
    void toleranceAcceptsLimits() {
        assertThat(new BorderTolerance(0).meters()).isZero();
        assertThat(new BorderTolerance(5000).meters()).isEqualTo(5000);
    }

    @Test
    void toleranceRejectsOutOfRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BorderTolerance(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> new BorderTolerance(5001));
    }

    @Test
    void zoneIdWrapsUuidAndRejectsNull() {
        var uuid = UUID.randomUUID();

        assertThat(new DeliveryZoneId(uuid).value()).isEqualTo(uuid);
        assertThatNullPointerException().isThrownBy(() -> new DeliveryZoneId(null));
    }

    @Test
    void enumsExposeExpectedValues() {
        assertThat(ResolutionMethod.values()).extracting(Enum::name)
                .containsExactly("ZONE_AREA", "COMUNA_BOUNDARY", "NEAREST_COMUNA", "COMUNA_CODE");
        assertThat(NotCoveredReason.values()).extracting(Enum::name)
                .containsExactly("EXCLUDED_AREA", "OUTSIDE_SERVICE_AREA", "COMUNA_NOT_SERVED", "ZONE_INACTIVE");
    }
}
