package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Contrato del adaptador de comunas contra PostGIS real (3.1, 3.2, 5.4, 5.5, 5.12, 10.3).
 */
@IntegrationTest
class ComunaLookupRepositoryImplIT {

    @Autowired
    private ComunaLookupRepository comunas;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void findsAComunaByCodeWithItsProvinceAndBoundaryFlag() {
        Comuna nunoa = comunas.findByCode(new ComunaCode("13120")).orElseThrow();

        assertThat(nunoa.name()).isEqualTo("Ñuñoa");
        assertThat(nunoa.provinceCode().value()).isEqualTo("131");
        assertThat(nunoa.provinceName()).isEqualTo("Santiago");
        assertThat(nunoa.hasBoundary()).isTrue();
    }

    @Test
    void flagsComunasWithoutBoundary() {
        Comuna antartica = comunas.findByCode(new ComunaCode("12202")).orElseThrow();

        assertThat(antartica.hasBoundary()).isFalse();
    }

    @Test
    void returnsEmptyForUnknownComuna() {
        assertThat(comunas.findByCode(new ComunaCode("99999"))).isEmpty();
    }

    @Test
    void listsTheComunasOfARegion() {
        int expected = jdbc.queryForObject("select count(*) from geo_comuna where left(code, 2) = '13'",
                Integer.class);

        List<Comuna> found = comunas.findByRegion(new RegionCode("13"));

        assertThat(found).hasSize(expected).isNotEmpty();
        assertThat(found).allSatisfy(c -> assertThat(c.regionCode().value()).isEqualTo("13"));
        assertThat(found).extracting(c -> c.code().value()).doesNotHaveDuplicates();
    }

    @Test
    void returnsNoComunasForAnEmptyRegion() {
        assertThat(comunas.findByRegion(new RegionCode("99"))).isEmpty();
    }

    @Test
    void findsTheComunaCoveringAnInteriorPoint() {
        GeoPoint inside = pointOnSurface("13120");

        assertThat(comunas.findCovering(inside)).map(c -> c.code().value()).contains("13120");
    }

    @Test
    void findsNoComunaForAPointOutsideEveryBoundary() {
        GeoPoint buenosAires = new GeoPoint(-34.6037, -58.3816);

        assertThat(comunas.findCovering(buenosAires)).isEmpty();
    }

    @Test
    void coversAPointExactlyOnTheBoundaryAndBreaksTiesByLowestCode() {
        // Un vértice compartido o de borde queda cubierto (ST_Covers incluye el borde).
        var vertex = jdbc.queryForMap("select ST_X(v.geom) as x, ST_Y(v.geom) as y from geo_comuna_boundary b, "
                + "lateral ST_DumpPoints(b.geom) v where b.comuna_code = '13120' order by v.path limit 1");
        GeoPoint onBorder = new GeoPoint(((Number) vertex.get("y")).doubleValue(),
                ((Number) vertex.get("x")).doubleValue());

        List<String> covering = jdbc.queryForList("select comuna_code from geo_comuna_boundary "
                + "where ST_Covers(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)) order by comuna_code",
                String.class, onBorder.longitude(), onBorder.latitude());

        assertThat(covering).contains("13120");
        assertThat(comunas.findCovering(onBorder)).map(c -> c.code().value()).contains(covering.getFirst());
    }

    @Test
    void assignsANeighbouringComunaWithinToleranceButNotBeyondIt() {
        Western west = westernmostVertexOfRegion04();
        GeoPoint at100m = project(west, 100);
        GeoPoint at1km = project(west, 1000);

        assertThat(comunas.findCovering(at100m)).isEmpty();
        assertThat(comunas.findCovering(at1km)).isEmpty();

        assertThat(comunas.findNearestWithin(at100m, new BorderTolerance(300)))
                .map(c -> c.code().value()).contains(west.comunaCode());
        assertThat(comunas.findNearestWithin(at100m, new BorderTolerance(50))).isEmpty();
        assertThat(comunas.findNearestWithin(at1km, new BorderTolerance(300))).isEmpty();
        assertThat(comunas.findNearestWithin(at1km, new BorderTolerance(1500)))
                .map(c -> c.code().value()).contains(west.comunaCode());
    }

    @Test
    void nearestWithinReturnsTheCoveringComunaForAnInteriorPoint() {
        Optional<Comuna> nearest = comunas.findNearestWithin(pointOnSurface("13120"), new BorderTolerance(0));

        assertThat(nearest).map(c -> c.code().value()).contains("13120");
    }

    private GeoPoint pointOnSurface(String comunaCode) {
        var row = jdbc.queryForMap("select ST_X(p) as x, ST_Y(p) as y from "
                + "(select ST_PointOnSurface(geom) as p from geo_comuna_boundary where comuna_code = ?) s",
                comunaCode);
        return new GeoPoint(((Number) row.get("y")).doubleValue(), ((Number) row.get("x")).doubleValue());
    }

    private Western westernmostVertexOfRegion04() {
        var row = jdbc.queryForMap("select b.comuna_code as code, ST_X(v.geom) as x, ST_Y(v.geom) as y "
                + "from geo_comuna_boundary b, lateral ST_DumpPoints(b.geom) v "
                + "where left(b.comuna_code, 2) = '04' order by ST_X(v.geom), b.comuna_code limit 1");
        return new Western(((String) row.get("code")), ((Number) row.get("y")).doubleValue(),
                ((Number) row.get("x")).doubleValue());
    }

    /** Punto a {@code meters} metros geodésicos al oeste del vértice más occidental (mar abierto). */
    private GeoPoint project(Western vertex, double meters) {
        var row = jdbc.queryForMap("select ST_X(g) as x, ST_Y(g) as y from (select ST_Project("
                + "ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, radians(270))::geometry as g) s",
                vertex.longitude(), vertex.latitude(), meters);
        return new GeoPoint(((Number) row.get("y")).doubleValue(), ((Number) row.get("x")).doubleValue());
    }

    private record Western(String comunaCode, double latitude, double longitude) {
    }
}
