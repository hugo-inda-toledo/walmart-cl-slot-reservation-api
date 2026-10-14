package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeoCatalogTypesTest {

    @Test
    void regionValidatesFields() {
        var region = new Region(new RegionCode("13"), "Región Metropolitana de Santiago", 8);
        assertThat(region.northToSouthOrder()).isEqualTo(8);
        assertThatThrownBy(() -> new Region(null, "x", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Region(new RegionCode("13"), " ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comunaValidatesFields() {
        var code = new ComunaCode("13101");
        var comuna = new Comuna(code, "Santiago", code.provinceCode(), "Santiago", true);
        assertThat(comuna.hasBoundary()).isTrue();
        assertThatThrownBy(() -> new Comuna(code, "", code.provinceCode(), "Santiago", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Comuna(code, "Santiago", null, "Santiago", true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zoneGeometryRequiresText() {
        assertThat(new ZoneGeometry("{\"type\":\"MultiPolygon\",\"coordinates\":[]}").geoJson()).contains("MultiPolygon");
        assertThatThrownBy(() -> new ZoneGeometry(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ZoneGeometry(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void expectedCoverageCoherence() {
        var covered = new DemoAddress.ExpectedCoverage(true, Optional.of("RM_CENTRO"),
                Optional.of(ResolutionMethod.COMUNA_BOUNDARY), Optional.empty());
        assertThat(covered.covered()).isTrue();

        var notCovered = new DemoAddress.ExpectedCoverage(false, Optional.empty(), Optional.empty(),
                Optional.of(NotCoveredReason.EXCLUDED_AREA));
        assertThat(notCovered.reason()).contains(NotCoveredReason.EXCLUDED_AREA);

        assertThatThrownBy(() -> new DemoAddress.ExpectedCoverage(true, Optional.empty(), Optional.empty(),
                Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoAddress.ExpectedCoverage(true, Optional.of("Z1"),
                Optional.of(ResolutionMethod.ZONE_AREA), Optional.of(NotCoveredReason.EXCLUDED_AREA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoAddress.ExpectedCoverage(false, Optional.empty(), Optional.empty(),
                Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DemoAddress.ExpectedCoverage(false, Optional.of("Z1"), Optional.empty(),
                Optional.of(NotCoveredReason.ZONE_INACTIVE))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void demoAddressHoldsOptionalPoint() {
        var expected = new DemoAddress.ExpectedCoverage(false, Optional.empty(), Optional.empty(),
                Optional.of(NotCoveredReason.OUTSIDE_SERVICE_AREA));
        var demo = new DemoAddress("demo-1", "Fuera", "Av. Siempre Viva", "742", new ComunaCode("13101"),
                "Santiago", Optional.of(new GeoPoint(-33.4, -70.6)), expected);
        assertThat(demo.point()).isPresent();
        assertThatThrownBy(() -> new DemoAddress(" ", "l", "s", "1", new ComunaCode("13101"), "Santiago",
                Optional.empty(), expected)).isInstanceOf(IllegalArgumentException.class);
    }
}
