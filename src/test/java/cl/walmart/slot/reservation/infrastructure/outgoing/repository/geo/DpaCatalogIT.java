package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DpaCatalogIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsTheCompleteOfficialDivision() {
        assertThat(count("select count(*) from geo_region")).isEqualTo(16);
        assertThat(count("select count(*) from geo_province")).isEqualTo(56);
        assertThat(count("select count(*) from geo_comuna")).isEqualTo(346);
    }

    @Test
    void includesNubleWithItsThreeProvinces() {
        assertThat(jdbc.queryForObject("select name from geo_region where code = '16'", String.class))
                .isEqualTo("Región de Ñuble");
        assertThat(jdbc.queryForList("select name from geo_province where region_code = '16' order by code",
                String.class))
                .containsExactly("Diguillín", "Itata", "Punilla");
        assertThat(count("select count(*) from geo_comuna where left(code, 2) = '16'")).isEqualTo(21);
    }

    @Test
    void namesEveryProvinceUniquely() {
        assertThat(jdbc.queryForList("select name from geo_province where region_code = '01' order by code",
                String.class))
                .containsExactly("Iquique", "Tamarugal");
        assertThat(count("select count(*) from (select name from geo_province group by name having count(*) > 1) d"))
                .isZero();
    }

    @Test
    void ordersRegionsGeographicallyFromNorthToSouth() {
        List<String> codes = jdbc.queryForList(
                "select code from geo_region order by north_to_south_order", String.class);

        assertThat(codes).containsExactly(
                "15", "01", "02", "03", "04", "05", "13", "06", "07", "16", "08", "09", "14", "10", "11", "12");
    }

    @Test
    void keepsCodePrefixesConsistentAcrossTheHierarchy() {
        assertThat(count("select count(*) from geo_comuna c join geo_province p on p.code = c.province_code "
                + "where left(c.code, 3) <> p.code or left(c.code, 2) <> p.region_code")).isZero();
        assertThat(count("select count(*) from geo_comuna where code !~ '^[0-9]{5}$'")).isZero();
        assertThat(count("select count(*) from geo_province p "
                + "where not exists (select 1 from geo_comuna c where c.province_code = p.code)")).isZero();
        assertThat(count("select count(*) from geo_region r "
                + "where not exists (select 1 from geo_province p where p.region_code = r.code)")).isZero();
    }

    @Test
    void storesOfficialNamesWithAccentsAndEnye() {
        assertThat(nameOf("13120")).isEqualTo("Ñuñoa");
        assertThat(nameOf("13122")).isEqualTo("Peñalolén");
        assertThat(nameOf("08301")).isEqualTo("Los Ángeles");
        assertThat(nameOf("16303")).isEqualTo("Ñiquén");
        assertThat(nameOf("04101")).isEqualTo("La Serena");
        assertThat(nameOf("12202")).isEqualTo("Antártica");
        assertThat(jdbc.queryForObject("select name from geo_region where code = '06'", String.class))
                .isEqualTo("Región del Libertador General Bernardo O'Higgins");
    }

    @Test
    void countsTheComunasOfTheRegionsWithBoundaries() {
        assertThat(count("select count(*) from geo_comuna where left(code, 2) = '04'")).isEqualTo(15);
        assertThat(count("select count(*) from geo_comuna where left(code, 2) = '13'")).isEqualTo(52);
    }

    @Test
    void storesBoundariesExactlyForTheComunasOfCoquimboAndMetropolitana() {
        assertThat(count("select count(*) from geo_comuna_boundary")).isEqualTo(67);
        assertThat(count("select count(*) from geo_comuna_boundary where left(comuna_code, 2) = '04'")).isEqualTo(15);
        assertThat(count("select count(*) from geo_comuna_boundary where left(comuna_code, 2) = '13'")).isEqualTo(52);
        assertThat(count("select count(*) from geo_comuna_boundary where left(comuna_code, 2) not in ('04', '13')"))
                .isZero();
        assertThat(count("select count(*) from geo_comuna c where left(c.code, 2) in ('04', '13') "
                + "and not exists (select 1 from geo_comuna_boundary b where b.comuna_code = c.code)")).isZero();
    }

    @Test
    void expressesBoundariesInWgs84() {
        assertThat(count("select count(*) from geo_comuna_boundary where ST_SRID(geom) <> 4326")).isZero();
        assertThat(count("select count(*) from geo_comuna_boundary "
                + "where ST_XMin(geom) < -73 or ST_XMax(geom) > -69 or ST_YMin(geom) < -35 or ST_YMax(geom) > -29"))
                .isZero();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String nameOf(String comunaCode) {
        return jdbc.queryForObject("select name from geo_comuna where code = ?", String.class, comunaCode);
    }
}
