package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato del adaptador de zonas contra PostGIS real (4.4, 5.2, 5.3, 5.12, 7.1 a 7.5, 10.3).
 * Inserta sus propias filas dentro de una transacción que se revierte para no alterar el seed.
 */
@IntegrationTest
@Transactional
class ZoneLookupRepositoryImplIT {

    private static final String ZONE_A = "00000000-0000-4000-8000-0000000000a1";
    private static final String ZONE_B = "00000000-0000-4000-8000-0000000000b1";
    private static final String ZONE_C = "00000000-0000-4000-8000-0000000000c1";
    private static final String ZONE_D = "00000000-0000-4000-8000-0000000000d1";
    private static final String ZONE_E = "00000000-0000-4000-8000-0000000000e1";
    private static final String ZONE_F = "00000000-0000-4000-8000-0000000000f1";

    private static final String CHICUREO_ID = "5f1c0e2a-0000-4000-8000-000000001303";
    private static final String LA_SERENA_ID = "5f1c0e2a-0000-4000-8000-000000000401";

    // Cuadrados en pleno desierto, lejos de cualquier área sembrada (lon, lat).
    private static final String BIG = square(-70.00, -30.00, -69.90, -29.90);
    private static final String HOLE = square(-69.97, -29.97, -69.93, -29.93);
    private static final String SMALL = square(-69.99, -29.99, -69.98, -29.98);
    private static final String INACTIVE_AREA = square(-69.80, -30.00, -69.70, -29.90);
    private static final String COMUNA_SQUARE = square(-69.60, -30.00, -69.50, -29.90);

    @Autowired
    private ZoneLookupRepository zones;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertFixtures() {
        jdbc.update("insert into geo_region (code, name, north_to_south_order) values ('99', 'Región de prueba', 99)");
        jdbc.update("insert into geo_province (code, region_code, name) values ('991', '99', 'Provincia de prueba')");
        for (String code : List.of("99101", "99102", "99103", "99104")) {
            jdbc.update("insert into geo_comuna (code, province_code, name) values (?, '991', ?)",
                    code, "Comuna " + code);
        }
        jdbc.update("insert into geo_dispatch_node (code, name) values ('CD-TEST', 'CD de prueba')");

        zone(ZONE_A, "TEST_A", "ACTIVE");
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99101', ?::uuid)", ZONE_A);
        area(ZONE_A, "INCLUSION", BIG);
        area(ZONE_A, "EXCLUSION", HOLE);

        zone(ZONE_B, "TEST_B", "INACTIVE");
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99102', ?::uuid)", ZONE_B);
        area(ZONE_B, "INCLUSION", INACTIVE_AREA);
        area(ZONE_B, "EXCLUSION", INACTIVE_AREA);

        // D se inserta antes que C: el desempate por código debe ganar al orden de inserción.
        zone(ZONE_D, "TEST_D", "ACTIVE");
        area(ZONE_D, "INCLUSION", SMALL);
        zone(ZONE_C, "TEST_C", "ACTIVE");
        area(ZONE_C, "INCLUSION", SMALL);

        zone(ZONE_E, "TEST_E", "ACTIVE");
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99103', ?::uuid)", ZONE_E);

        zone(ZONE_F, "TEST_F", "ACTIVE");
        jdbc.update("insert into geo_delivery_zone_comuna (comuna_code, zone_id) values ('99104', ?::uuid)", ZONE_F);
        jdbc.update("insert into geo_comuna_boundary (comuna_code, geom) values ('99104', " + COMUNA_SQUARE + ")");
    }

    @Test
    void listsZonesOrderedByCodeWithOptionalStatusFilter() {
        List<String> all = codes(zones.findAll(Optional.empty()));
        assertThat(all).isSorted().contains("CHICUREO", "MAIPU", "TEST_A", "TEST_B");

        List<String> active = codes(zones.findAll(Optional.of(ZoneStatus.ACTIVE)));
        assertThat(active).isSorted().contains("CHICUREO", "TEST_A").doesNotContain("MAIPU", "TEST_B");

        List<String> inactive = codes(zones.findAll(Optional.of(ZoneStatus.INACTIVE)));
        assertThat(inactive).isSorted().contains("MAIPU", "TEST_B").doesNotContain("CHICUREO", "TEST_A");
    }

    @Test
    void findsAZoneByIdWithNodeComunasAndAreaCounts() {
        DeliveryZone laSerena = zones.findById(id(LA_SERENA_ID)).orElseThrow();

        assertThat(laSerena.code()).isEqualTo("LA_SERENA_COQUIMBO");
        assertThat(laSerena.status()).isEqualTo(ZoneStatus.ACTIVE);
        assertThat(laSerena.node().code()).isEqualTo("CD-LSR");
        assertThat(laSerena.comunas()).extracting(c -> c.code().value()).containsExactly("04101", "04102");
        assertThat(laSerena.inclusionAreaCount()).isZero();
        assertThat(laSerena.exclusionAreaCount()).isEqualTo(1);

        DeliveryZone chicureo = zones.findById(id(CHICUREO_ID)).orElseThrow();
        assertThat(chicureo.comunas()).isEmpty();
        assertThat(chicureo.inclusionAreaCount()).isEqualTo(1);
        assertThat(chicureo.exclusionAreaCount()).isZero();

        DeliveryZone testA = zones.findById(id(ZONE_A)).orElseThrow();
        assertThat(testA.inclusionAreaCount()).isEqualTo(1);
        assertThat(testA.exclusionAreaCount()).isEqualTo(1);
    }

    @Test
    void returnsEmptyForAnUnknownZone() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-00000000ffff");

        assertThat(zones.findById(new DeliveryZoneId(unknown))).isEmpty();
        assertThat(zones.findGeometry(new DeliveryZoneId(unknown))).isEmpty();
    }

    @Test
    void findsTheZoneAssignedToAComunaInAnyStatus() {
        assertThat(zones.findAssignedTo(new ComunaCode("13120"))).map(DeliveryZone::code)
                .contains("SANTIAGO_ORIENTE");
        assertThat(zones.findAssignedTo(new ComunaCode("13119"))).map(DeliveryZone::code).contains("MAIPU");
        assertThat(zones.findAssignedTo(new ComunaCode("99102"))).map(DeliveryZone::status)
                .contains(ZoneStatus.INACTIVE);
        assertThat(zones.findAssignedTo(new ComunaCode("13101"))).map(DeliveryZone::code)
                .contains("SANTIAGO_CENTRO");
    }

    @Test
    void returnsEmptyForAComunaWithoutZone() {
        assertThat(zones.findAssignedTo(new ComunaCode("99999"))).isEmpty();
        assertThat(zones.findAssignedTo(new ComunaCode("13301"))).isEmpty();
    }

    @Test
    void findsTheActiveZoneWhoseInclusionOrExclusionCoversAPoint() {
        GeoPoint inHole = new GeoPoint(-29.95, -69.95);
        GeoPoint inRing = new GeoPoint(-29.92, -69.92);

        assertThat(zones.findActiveAreaCovering(inHole, ZoneAreaKind.EXCLUSION)).map(DeliveryZone::code)
                .contains("TEST_A");
        assertThat(zones.findActiveAreaCovering(inRing, ZoneAreaKind.EXCLUSION)).isEmpty();
        assertThat(zones.findActiveAreaCovering(inRing, ZoneAreaKind.INCLUSION)).map(DeliveryZone::code)
                .contains("TEST_A");
        assertThat(zones.findActiveAreaCovering(new GeoPoint(-20.0, -60.0), ZoneAreaKind.INCLUSION)).isEmpty();
    }

    @Test
    void ignoresAreasOfInactiveZones() {
        GeoPoint inInactiveArea = new GeoPoint(-29.95, -69.75);

        assertThat(zones.findActiveAreaCovering(inInactiveArea, ZoneAreaKind.EXCLUSION)).isEmpty();
        assertThat(zones.findActiveAreaCovering(inInactiveArea, ZoneAreaKind.INCLUSION)).isEmpty();
    }

    @Test
    void breaksOverlappingInclusionsByLowestAreaThenCode() {
        GeoPoint inSmall = new GeoPoint(-29.985, -69.985);

        // BIG (TEST_A) contiene a SMALL: gana el área menor; entre áreas iguales, el menor código.
        assertThat(zones.findActiveAreaCovering(inSmall, ZoneAreaKind.INCLUSION)).map(DeliveryZone::code)
                .contains("TEST_C");
    }

    @Test
    void exposesTheZoneGeometryAsMultiPolygonGeoJsonWithExclusionsSubtracted() {
        Optional<ZoneGeometry> geometry = zones.findGeometry(id(ZONE_A));

        assertThat(geometry).isPresent();
        String geoJson = geometry.get().geoJson();
        assertThat(jdbc.queryForObject("select ST_GeometryType(ST_GeomFromGeoJSON(?))", String.class, geoJson))
                .isEqualTo("ST_MultiPolygon");
        double actual = jdbc.queryForObject("select ST_Area(ST_GeomFromGeoJSON(?))", Double.class, geoJson);
        double expected = jdbc.queryForObject("select ST_Area(" + BIG + ") - ST_Area(" + HOLE + ")", Double.class);
        assertThat(actual).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(jdbc.queryForObject("select ST_Covers(ST_GeomFromGeoJSON(?), ST_SetSRID(ST_MakePoint(-69.95, "
                + "-29.95), 4326))", Boolean.class, geoJson)).isFalse();
    }

    @Test
    void buildsTheGeometryFromAComunaBoundary() {
        Optional<ZoneGeometry> geometry = zones.findGeometry(id(ZONE_F));

        assertThat(geometry).isPresent();
        assertThat(jdbc.queryForObject("select ST_Equals(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), " + COMUNA_SQUARE
                + ")", Boolean.class, geometry.get().geoJson())).isTrue();
    }

    @Test
    void omitsTheGeometryWhenTheZoneHasNoPolygons() {
        assertThat(zones.findGeometry(id(ZONE_E))).isEmpty();
    }

    @Test
    void omitsTheGeometryWhenExclusionsCancelEveryInclusion() {
        String zoneG = "00000000-0000-4000-8000-0000000000a9";
        zone(zoneG, "TEST_G", "ACTIVE");
        area(zoneG, "INCLUSION", SMALL);
        area(zoneG, "EXCLUSION", BIG);

        assertThat(zones.findGeometry(id(zoneG))).isEmpty();
    }

    private List<String> codes(List<DeliveryZone> found) {
        return found.stream().map(DeliveryZone::code).toList();
    }

    private static DeliveryZoneId id(String uuid) {
        return new DeliveryZoneId(UUID.fromString(uuid));
    }

    private void zone(String id, String code, String status) {
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, ?, ?, ?, 'CD-TEST')", id, code, "Zona " + code, status);
    }

    private void area(String zoneId, String kind, String geom) {
        jdbc.update("insert into geo_delivery_zone_area (id, zone_id, kind, name, geom) "
                + "values (gen_random_uuid(), ?::uuid, ?, 'Área de prueba', " + geom + ")", zoneId, kind);
    }

    private static String square(double west, double south, double east, double north) {
        return "ST_GeomFromText('MULTIPOLYGON(((" + west + " " + south + ", " + east + " " + south + ", " + east
                + " " + north + ", " + west + " " + north + ", " + west + " " + south + ")))', 4326)";
    }
}
