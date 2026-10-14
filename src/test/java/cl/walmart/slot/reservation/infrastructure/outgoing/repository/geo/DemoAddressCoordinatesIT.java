package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the seeded demo coordinates against the seeded boundaries and areas with direct PostGIS SQL, so a
 * regenerated boundary that moves a demo point is detected independently of the resolution code.
 */
@IntegrationTest
class DemoAddressCoordinatesIT {

    private static final int BORDER_TOLERANCE_METERS = 300;
    private static final String POINT = "ST_SetSRID(ST_MakePoint(d.longitude, d.latitude), 4326)";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsTheNineDemoAddressesInPresentationOrder() {
        assertThat(jdbc.queryForList("select id from geo_demo_address order by sort_order", String.class))
                .containsExactly("la-serena-monjitas", "santiago-monjitas", "providencia-av", "chicureo",
                        "la-serena-borde", "coquimbo-sin-coordenadas", "vina-sin-cobertura", "maipu-inactiva",
                        "tongoy-excluida");
        assertThat(jdbc.queryForObject("select count(*) from geo_demo_address where latitude is null",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void placesCoordinatesInsideTheComunaExpectedToResolveByBoundary() {
        List<Map<String, Object>> rows = jdbc.queryForList("select d.id, "
                + "exists (select 1 from geo_comuna_boundary b where b.comuna_code = d.comuna_code "
                + "and ST_Covers(b.geom, " + POINT + ")) as in_comuna, "
                + "exists (select 1 from geo_delivery_zone_area a where ST_Covers(a.geom, " + POINT + ")) as in_area, "
                + "z.code as zone_code, z.status as zone_status "
                + "from geo_demo_address d "
                + "left join geo_delivery_zone_comuna zc on zc.comuna_code = d.comuna_code "
                + "left join geo_delivery_zone z on z.id = zc.zone_id "
                + "where d.expected_method = 'COMUNA_BOUNDARY' or (d.expected_reason = 'ZONE_INACTIVE' "
                + "and d.latitude is not null) order by d.sort_order");
        SoftAssertions softly = new SoftAssertions();

        assertThat(rows).extracting(row -> row.get("id"))
                .containsExactly("la-serena-monjitas", "santiago-monjitas", "providencia-av", "maipu-inactiva");
        for (Map<String, Object> row : rows) {
            softly.assertThat(row.get("in_comuna")).as("%s inside its comuna", row.get("id")).isEqualTo(true);
            softly.assertThat(row.get("in_area")).as("%s outside every area", row.get("id")).isEqualTo(false);
        }
        softly.assertThat(rows).extracting(row -> row.get("zone_code") + ":" + row.get("zone_status"))
                .containsExactly("LA_SERENA_COQUIMBO:ACTIVE", "SANTIAGO_CENTRO:ACTIVE", "SANTIAGO_ORIENTE:ACTIVE",
                        "MAIPU:INACTIVE");
        softly.assertAll();
    }

    @Test
    void placesTheSubZoneAddressInsideTheActiveInclusionArea() {
        assertThat(areaContaining("chicureo")).isEqualTo("INCLUSION|CHICUREO|ACTIVE");
    }

    @Test
    void placesTheExcludedAddressInsideTheActiveExclusionArea() {
        assertThat(areaContaining("tongoy-excluida")).isEqualTo("EXCLUSION|LA_SERENA_COQUIMBO|ACTIVE");
    }

    @Test
    void placesTheBorderAddressOutsideEveryBoundaryButWithinToleranceOfItsComuna() {
        Map<String, Object> border = jdbc.queryForMap("select "
                + "(select count(*) from geo_comuna_boundary b where ST_Covers(b.geom, " + POINT + ")) as covering, "
                + "(select b.comuna_code from geo_comuna_boundary b "
                + "order by ST_Distance(b.geom::geography, " + POINT + "::geography), b.comuna_code limit 1) "
                + "as nearest, "
                + "(select ST_Distance(b.geom::geography, " + POINT + "::geography) from geo_comuna_boundary b "
                + "where b.comuna_code = d.comuna_code) as distance_meters, "
                + "(select count(*) from geo_delivery_zone_area a where ST_Covers(a.geom, " + POINT + ")) as areas "
                + "from geo_demo_address d where d.id = 'la-serena-borde'");

        assertThat(border).containsEntry("covering", 0L).containsEntry("nearest", "04101").containsEntry("areas", 0L);
        assertThat((Double) border.get("distance_meters")).isPositive().isLessThanOrEqualTo(BORDER_TOLERANCE_METERS);
    }

    @Test
    void matchesAddressesWithoutCoordinatesWithTheZoneOfTheirDeclaredComuna() {
        List<String> rows = jdbc.queryForList("select d.id || '|' || coalesce(z.code, '-') || '|' "
                + "|| coalesce(z.status, '-') from geo_demo_address d "
                + "left join geo_delivery_zone_comuna zc on zc.comuna_code = d.comuna_code "
                + "left join geo_delivery_zone z on z.id = zc.zone_id "
                + "where d.latitude is null order by d.sort_order", String.class);

        assertThat(rows).containsExactly(
                "coquimbo-sin-coordenadas|LA_SERENA_COQUIMBO|ACTIVE",
                "vina-sin-cobertura|LIDER_REGION_05|ACTIVE");
    }

    private String areaContaining(String demoAddressId) {
        return jdbc.queryForObject("select string_agg(a.kind || '|' || z.code || '|' || z.status, ',') "
                + "from geo_demo_address d join geo_delivery_zone_area a on ST_Covers(a.geom, " + POINT + ") "
                + "join geo_delivery_zone z on z.id = a.zone_id where d.id = ?", String.class, demoAddressId);
    }
}
