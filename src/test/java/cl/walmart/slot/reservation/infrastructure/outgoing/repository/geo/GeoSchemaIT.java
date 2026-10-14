package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class GeoSchemaIT {

    private static final String ZONE_A = "00000000-0000-4000-8000-0000000000a1";
    private static final String ZONE_B = "00000000-0000-4000-8000-0000000000b1";
    private static final String SQUARE =
            "ST_GeomFromText('MULTIPOLYGON(((-70 -30, -69.9 -30, -69.9 -29.9, -70 -29.9, -70 -30)))', 4326)";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertFixtureHierarchy() {
        jdbc.update("insert into geo_region (code, name, north_to_south_order) values ('99', 'Región de prueba', 99)");
        jdbc.update("insert into geo_province (code, region_code, name) values ('991', '99', 'Provincia de prueba')");
        jdbc.update("insert into geo_comuna (code, province_code, name) values ('99101', '991', 'Comuna de prueba')");
        jdbc.update("insert into geo_dispatch_node (code, name) values ('CD-TEST', 'CD de prueba')");
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, 'TEST_A', 'Zona A', 'ACTIVE', 'CD-TEST')", ZONE_A);
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, 'TEST_B', 'Zona B', 'INACTIVE', 'CD-TEST')", ZONE_B);
    }

    @Test
    void createsEveryGeoTable() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_name like 'geo\\_%' order by table_name",
                String.class);

        assertThat(tables).containsExactly(
                "geo_comuna", "geo_comuna_boundary", "geo_coverage_provenance", "geo_delivery_zone", "geo_delivery_zone_area",
                "geo_delivery_zone_comuna", "geo_demo_address", "geo_dispatch_node", "geo_province", "geo_region");
    }

    @Test
    void declaresMultiPolygonGeometriesInWgs84WithGistIndexes() {
        List<String> columns = jdbc.queryForList(
                "select f_table_name || '.' || type || '.' || srid from geometry_columns "
                        + "where f_table_name like 'geo\\_%' order by f_table_name",
                String.class);
        List<String> gistIndexes = jdbc.queryForList(
                "select indexname from pg_indexes where tablename like 'geo\\_%' and indexdef like '%USING gist%' "
                        + "order by indexname",
                String.class);

        assertThat(columns).containsExactly(
                "geo_comuna_boundary.MULTIPOLYGON.4326", "geo_delivery_zone_area.MULTIPOLYGON.4326");
        assertThat(gistIndexes).containsExactly("geo_comuna_boundary_geom_idx", "geo_delivery_zone_area_geom_idx");
    }

    @Test
    void rejectsCodesWhosePrefixDoesNotMatchTheirParent() {
        assertRejected("geo_comuna_code_prefix",
                "insert into geo_comuna (code, province_code, name) values ('98101', '991', 'Prefijo incorrecto')");
        assertRejected("geo_province_code_prefix",
                "insert into geo_province (code, region_code, name) values ('981', '99', 'Prefijo incorrecto')");
    }

    @Test
    void rejectsMalformedCodes() {
        assertRejected("geo_region_code_format",
                "insert into geo_region (code, name, north_to_south_order) values ('9A', 'Mal formada', 98)");
    }

    @Test
    void assignsACompleteComunaToAtMostOneZone() {
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99101', ?::uuid)", ZONE_A);

        assertRejected("geo_delivery_zone_comuna_pkey",
                "insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99101', ?::uuid)", ZONE_B);
    }

    @Test
    void restrictsZoneStatusAndAreaKind() {
        assertRejected("geo_delivery_zone_status_allowed",
                "insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                        + "values (gen_random_uuid(), 'TEST_C', 'Zona C', 'PAUSED', 'CD-TEST')");
        assertRejected("geo_delivery_zone_area_kind_allowed",
                "insert into geo_delivery_zone_area (id, zone_id, kind, name, geom) "
                        + "values (gen_random_uuid(), ?::uuid, 'BUFFER', 'Área', " + SQUARE + ")",
                ZONE_A);
    }

    @Test
    void rejectsInvalidGeometriesAndOtherSrids() {
        String bowTie =
                "ST_GeomFromText('MULTIPOLYGON(((-70 -30, -69.9 -29.9, -69.9 -30, -70 -29.9, -70 -30)))', 4326)";

        assertRejected("geo_delivery_zone_area_geom_valid",
                "insert into geo_delivery_zone_area (id, zone_id, kind, name, geom) "
                        + "values (gen_random_uuid(), ?::uuid, 'INCLUSION', 'Corbatín', " + bowTie + ")",
                ZONE_A);
        assertRejected("SRID",
                "insert into geo_comuna_boundary (comuna_code, geom) "
                        + "values ('99101', ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 0)))', 3857))");
    }

    @Test
    void requiresBothOrNoDemoCoordinatesAndAConsistentExpectedResult() {
        String insert = "insert into geo_demo_address (id, label, street, number, comuna_code, latitude, longitude, "
                + "expected_covered, expected_zone_code, expected_method, expected_reason, sort_order) "
                + "values (?, 'Etiqueta', 'Calle', '1', '99101', ?, ?, ?, ?, ?, ?, ?)";

        // Sort orders start at 101 to stay clear of the seeded demo addresses.
        jdbc.update(insert, "complete", -30.0, -70.0, true, "TEST_A", "COMUNA_BOUNDARY", null, 101);
        jdbc.update(insert, "no-coordinates", null, null, false, null, null, "COMUNA_NOT_SERVED", 102);

        assertRejected("geo_demo_address_coordinates_paired", insert,
                "half", -30.0, null, true, "TEST_A", "COMUNA_CODE", null, 103);
        assertRejected("geo_demo_address_expected_consistent", insert,
                "covered-with-reason", null, null, true, "TEST_A", "COMUNA_CODE", "ZONE_INACTIVE", 104);
        assertRejected("geo_demo_address_expected_consistent", insert,
                "uncovered-with-zone", null, null, false, "TEST_A", null, "ZONE_INACTIVE", 105);
    }

    /** Runs a statement expected to violate {@code constraint} inside a savepoint, so the test transaction survives. */
    private void assertRejected(String constraint, String sql, Object... args) {
        jdbc.execute("savepoint rejected_statement");
        try {
            assertThatThrownBy(() -> jdbc.update(sql, args))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(constraint);
        } finally {
            jdbc.execute("rollback to savepoint rejected_statement");
        }
    }
}
