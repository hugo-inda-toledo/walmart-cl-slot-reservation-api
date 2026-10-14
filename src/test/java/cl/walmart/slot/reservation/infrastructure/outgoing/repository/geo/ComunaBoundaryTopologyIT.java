package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that boundary simplification (mapshaper over shared arcs) preserves adjacency between comunas.
 *
 * <p>Thresholds calibrated against the data generated in {@code V2_3} (BCN, 8% of vertices, 6 decimals): the
 * measurement gave 0 m² of overlap between neighbouring comunas and no interior hole in the union of each region.
 * The 1,000 m² total-area margin and the 30 m maximum width (an order of magnitude below the 300 m border
 * tolerance) absorb 6-decimal rounding (~0.1 m) without accepting gaps or overlaps the tolerance would not cover.
 */
@IntegrationTest
class ComunaBoundaryTopologyIT {

    private static final double MAX_TOTAL_AREA_SQUARE_METERS = 1_000;
    private static final double MAX_WIDTH_METERS = 30;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesOnlyValidNonEmptyGeometries() {
        assertThat(count("select count(*) from geo_comuna_boundary where not ST_IsValid(geom) or ST_IsEmpty(geom)"))
                .isZero();
    }

    @Test
    void keepsNeighbouringComunasAdjacent() {
        assertThat(count("select count(*) from geo_comuna_boundary a join geo_comuna_boundary b "
                + "on a.comuna_code < b.comuna_code and ST_Intersects(a.geom, b.geom)"))
                .as("neighbouring comuna pairs sharing a border")
                .isGreaterThan(150);
    }

    @Test
    void overlapsBetweenNeighbouringComunasStayBelowTheThreshold() {
        String overlaps = "select (ST_Dump(ST_CollectionExtract(ST_Intersection(a.geom, b.geom), 3))).geom as g "
                + "from geo_comuna_boundary a join geo_comuna_boundary b "
                + "on a.comuna_code < b.comuna_code and ST_Intersects(a.geom, b.geom)";

        assertThat(totalAreaSquareMeters(overlaps)).isLessThanOrEqualTo(MAX_TOTAL_AREA_SQUARE_METERS);
        assertThat(maxWidthMeters(overlaps)).isLessThanOrEqualTo(MAX_WIDTH_METERS);
    }

    @Test
    void gapsBetweenNeighbouringComunasStayBelowTheThreshold() {
        String gaps = "with parts as (select (ST_Dump(ST_Union(geom))).geom as g from geo_comuna_boundary "
                + "group by left(comuna_code, 2)) "
                + "select ST_MakePolygon(ST_InteriorRingN(g, n)) as g from parts, "
                + "generate_series(1, ST_NumInteriorRings(g)) as n";

        assertThat(totalAreaSquareMeters(gaps)).isLessThanOrEqualTo(MAX_TOTAL_AREA_SQUARE_METERS);
        assertThat(maxWidthMeters(gaps)).isLessThanOrEqualTo(MAX_WIDTH_METERS);
    }

    @Test
    void leavesNoUncoveredGapBetweenNearbyComunasThatDoNotTouch() {
        // A gap opening onto the coast or the region edge is not an interior hole of the union. It would show up as
        // two comunas that stop touching while staying within the border tolerance, with land between them that
        // no comuna covers. Nearby pairs separated by a third comuna (e.g. La Reina between Ñuñoa and Las Condes)
        // are legitimate: their shortest connecting segment lies inside the other comunas.
        String nearbyPairs = "select ST_ShortestLine(a.geom, b.geom) as line from geo_comuna_boundary a "
                + "join geo_comuna_boundary b on a.comuna_code < b.comuna_code and not ST_Intersects(a.geom, b.geom) "
                + "and ST_DWithin(a.geom::geography, b.geom::geography, 300)";

        Double uncoveredMeters = jdbc.queryForObject("select coalesce(max(ST_Length(ST_Difference(p.line, "
                + "u.geom)::geography)), 0) from (" + nearbyPairs + ") p, "
                + "(select ST_Union(geom) as geom from geo_comuna_boundary) u", Double.class);

        assertThat(uncoveredMeters).isLessThanOrEqualTo(MAX_WIDTH_METERS);
    }

    private double totalAreaSquareMeters(String pieces) {
        return jdbc.queryForObject("select coalesce(sum(ST_Area(g::geography)), 0) from (" + pieces + ") p",
                Double.class);
    }

    /** Approximate width: diameter of the largest inscribed circle, measured in metres on the ellipsoid. */
    private double maxWidthMeters(String pieces) {
        return jdbc.queryForObject("select coalesce(max(2 * ST_Distance((c).center::geography, "
                + "(c).nearest::geography)), 0) "
                + "from (select ST_MaximumInscribedCircle(g) as c from (" + pieces + ") p) circles", Double.class);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
