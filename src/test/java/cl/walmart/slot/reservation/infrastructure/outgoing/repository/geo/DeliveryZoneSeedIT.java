package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DeliveryZoneSeedIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publishesTheDemoZonesWithTheirFixedIdentifiers() {
        List<String> zones = jdbc.queryForList("select id || '|' || code || '|' || name || '|' || status || '|' "
                + "|| dispatch_node_code from geo_delivery_zone where code not like 'LIDER_REGION_%' order by code", String.class);

        assertThat(zones).containsExactly(
                "5f1c0e2a-0000-4000-8000-000000001303|CHICUREO|Chicureo|ACTIVE|CD-STGO",
                "5f1c0e2a-0000-4000-8000-000000000401|LA_SERENA_COQUIMBO|La Serena-Coquimbo|ACTIVE|CD-LSR",
                "5f1c0e2a-0000-4000-8000-000000001304|MAIPU|Maipú|INACTIVE|CD-STGO",
                "5f1c0e2a-0000-4000-8000-000000001301|SANTIAGO_CENTRO|Santiago Centro|ACTIVE|CD-STGO",
                "5f1c0e2a-0000-4000-8000-000000001302|SANTIAGO_ORIENTE|Santiago Oriente|ACTIVE|CD-STGO");
        assertThat(jdbc.queryForList("select code || '|' || name from geo_dispatch_node "
                + "where code <> 'CD-NATIONAL' order by code", String.class))
                .containsExactly("CD-LSR|CD La Serena", "CD-STGO|CD Santiago");
    }

    @Test
    void assignsTheExpectedCompleteComunasToEachZone() {
        List<String> assignments = jdbc.queryForList("select z.code || ':' "
                + "|| string_agg(c.comuna_code, ',' order by c.comuna_code) from geo_delivery_zone z "
                + "join geo_delivery_zone_comuna c on c.zone_id = z.id "
                + "where z.code not like 'LIDER_REGION_%' group by z.code order by z.code", String.class);

        assertThat(assignments).containsExactly(
                "LA_SERENA_COQUIMBO:04101,04102",
                "MAIPU:13119",
                "SANTIAGO_CENTRO:13101,13106,13108,13127",
                "SANTIAGO_ORIENTE:13114,13120,13123,13132");
    }

    @Test
    void givesEveryZoneAtLeastOneCompleteComunaOrInclusionArea() {
        assertThat(jdbc.queryForObject("select count(*) from geo_delivery_zone z "
                + "where not exists (select 1 from geo_delivery_zone_comuna c where c.zone_id = z.id) "
                + "and not exists (select 1 from geo_delivery_zone_area a "
                + "where a.zone_id = z.id and a.kind = 'INCLUSION')", Integer.class)).isZero();
    }

    @Test
    void retainsAuditableProductConfirmationForMaculAndOfficialEvidenceForVinaDelMar() {
        assertThat(jdbc.queryForMap("select source_name, evidence, source_url, captured_on::text, note "
                + "from geo_coverage_provenance where comuna_code = '13118'"))
                .containsEntry("source_name", "Macul")
                .containsEntry("evidence", "PRODUCT_CONFIRMED")
                .containsEntry("captured_on", "2026-10-13")
                .containsEntry("source_url", null);
        assertThat(jdbc.queryForObject("select note from geo_coverage_provenance where comuna_code = '13118'",
                String.class)).contains("dueño de producto");
        assertThat(jdbc.queryForMap("select evidence, source_url, captured_on::text "
                + "from geo_coverage_provenance where comuna_code = '05109'"))
                .containsEntry("evidence", "OFFICIAL_PUBLIC")
                .containsEntry("source_url", "https://www.lider.cl/landing/static/zonas-de-cobertura/index.html")
                .containsEntry("captured_on", "2026-10-13");
    }

    @Test
    void assignsEveryValidatedSnapshotComunaToAnActiveZoneAndNoUnvalidatedComunaToNewZones() {
        assertThat(jdbc.queryForList("select evidence || ':' || count(*) from geo_coverage_provenance "
                + "group by evidence order by evidence", String.class))
                .containsExactly("OFFICIAL_PUBLIC:152", "PRODUCT_CONFIRMED:1");
        assertThat(jdbc.queryForObject("select count(*) from geo_coverage_provenance p "
                + "left join geo_delivery_zone_comuna c on c.comuna_code = p.comuna_code "
                + "left join geo_delivery_zone z on z.id = c.zone_id "
                + "where z.id is null or z.status <> 'ACTIVE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from geo_delivery_zone_comuna c "
                + "join geo_delivery_zone z on z.id = c.zone_id "
                + "left join geo_coverage_provenance p on p.comuna_code = c.comuna_code "
                + "where z.code like 'LIDER_REGION_%' and p.comuna_code is null", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select code from geo_delivery_zone "
                + "where code like 'LIDER_REGION_%' order by code", String.class)).containsExactly(
                "LIDER_REGION_01", "LIDER_REGION_02", "LIDER_REGION_03", "LIDER_REGION_04", "LIDER_REGION_05",
                "LIDER_REGION_06", "LIDER_REGION_07", "LIDER_REGION_08", "LIDER_REGION_09", "LIDER_REGION_10",
                "LIDER_REGION_12", "LIDER_REGION_13", "LIDER_REGION_14", "LIDER_REGION_15", "LIDER_REGION_16");
        assertThat(jdbc.queryForObject("select count(*) from geo_coverage_provenance where comuna_code = '11101'",
                Integer.class)).isZero();
    }

    @Test
    void includesAnInactiveZoneAnInclusionAreaAndAnExclusionArea() {
        assertThat(jdbc.queryForObject("select count(*) from geo_delivery_zone where status = 'INACTIVE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select z.code || '|' || a.kind || '|' || a.name from geo_delivery_zone_area a "
                + "join geo_delivery_zone z on z.id = a.zone_id order by z.code", String.class))
                .containsExactly(
                        "CHICUREO|INCLUSION|Chicureo",
                        "LA_SERENA_COQUIMBO|EXCLUSION|Tongoy-Guanaqueros rural");
    }

    @Test
    void keepsEachAreaInsideItsComunaBoundary() {
        Map<String, Object> chicureo = areaPlacement("Chicureo", "13301");
        Map<String, Object> tongoy = areaPlacement("Tongoy-Guanaqueros rural", "04102");

        assertThat(chicureo).containsEntry("covered_by", true).containsEntry("valid", true);
        assertThat(tongoy).containsEntry("covered_by", true).containsEntry("valid", true);
        assertThat((Double) chicureo.get("area_km2")).isGreaterThan(1.0);
        assertThat((Double) tongoy.get("area_km2")).isGreaterThan(1.0);
    }

    private Map<String, Object> areaPlacement(String areaName, String comunaCode) {
        return jdbc.queryForMap("select ST_CoveredBy(a.geom, b.geom) as covered_by, ST_IsValid(a.geom) as valid, "
                + "ST_Area(a.geom::geography) / 1e6 as area_km2 from geo_delivery_zone_area a, geo_comuna_boundary b "
                + "where a.name = ? and b.comuna_code = ?", areaName, comunaCode);
    }
}
