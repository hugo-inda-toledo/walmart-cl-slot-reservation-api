package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class HolidaySeedIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsTheNationalHolidaysOf2026And2027WithoutZoneBlocks() {
        assertThat(jdbc.queryForList("select extract(year from holiday_date)::int || ':' || count(*) "
                + "from catalog_holiday where zone_id is null group by extract(year from holiday_date) "
                + "order by extract(year from holiday_date)", String.class))
                .containsExactly("2026:16", "2027:17");
        assertThat(jdbc.queryForObject("select count(*) from catalog_holiday where zone_id is not null",
                Integer.class)).isZero();
    }

    @Test
    void includesMovedAndConditionalHolidays() {
        assertThat(jdbc.queryForList("select holiday_date || '|' || name from catalog_holiday "
                + "where zone_id is null and holiday_date in ('2026-10-12', '2027-06-28', '2027-09-17', "
                + "'2027-10-11', '2026-06-21') order by holiday_date", String.class))
                .containsExactly(
                        "2026-06-21|Día Nacional de los Pueblos Indígenas",
                        "2026-10-12|Encuentro de Dos Mundos",
                        "2027-06-28|San Pedro y San Pablo",
                        "2027-09-17|Feriado adicional de Fiestas Patrias",
                        "2027-10-11|Encuentro de Dos Mundos");
    }

    @Test
    void doesNotDeclareHolidaysOnTheOriginalDatesOfMovedOnes() {
        assertThat(jdbc.queryForObject("select count(*) from catalog_holiday "
                + "where holiday_date in ('2027-06-29', '2027-10-12', '2026-09-17')", Integer.class)).isZero();
    }
}
