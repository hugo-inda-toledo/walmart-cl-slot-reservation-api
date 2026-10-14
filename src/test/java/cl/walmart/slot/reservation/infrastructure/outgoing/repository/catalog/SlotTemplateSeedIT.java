package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SlotTemplateSeedIT {

    private static final String LA_SERENA = "5f1c0e2a-0000-4000-8000-000000000401";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void derivesEveryTemplateIdentifierFromItsZoneDayAndHours() {
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template t where t.id::text <> "
                + "'5f1c0e2a-0000-4000-8003-' || right(t.zone_id::text, 4) || lpad(t.day_of_week::text, 2, '0') "
                + "|| to_char(t.start_time, 'HH24') || to_char(t.end_time, 'HH24') || '00'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForList("select id::text || '|' || day_of_week || '|' || start_time || '|' || end_time "
                + "from catalog_slot_template where id in ('5f1c0e2a-0000-4000-8003-040101161800', "
                + "'5f1c0e2a-0000-4000-8003-130305151800') order by id", String.class))
                .containsExactly(
                        "5f1c0e2a-0000-4000-8003-040101161800|1|16:00:00|18:00:00",
                        "5f1c0e2a-0000-4000-8003-130305151800|5|15:00:00|18:00:00");
    }

    @Test
    void offersTheDemoTemplatesPerZone() {
        assertThat(jdbc.queryForList("select z.code || '|' || count(*) || '|' || count(distinct t.day_of_week) "
                + "|| '|' || min(t.price_clp) || '|' || max(t.capacity) from catalog_slot_template t "
                + "join geo_delivery_zone z on z.id = t.zone_id where z.code not like 'LIDER_REGION_%' "
                + "group by z.code order by z.code", String.class))
                .containsExactly(
                        "CHICUREO|10|5|4990|4",
                        "LA_SERENA_COQUIMBO|28|7|2990|8",
                        "SANTIAGO_CENTRO|24|6|3490|10",
                        "SANTIAGO_ORIENTE|21|7|1990|6");
    }

    @Test
    void publishesTheLaSerenaOverlappingBlocksEveryDayAt2990() {
        assertThat(jdbc.queryForList("select distinct to_char(start_time, 'HH24:MI') || '-' "
                + "|| to_char(end_time, 'HH24:MI') || '|' || price_clp from catalog_slot_template "
                + "where zone_id = ?::uuid order by 1", String.class, LA_SERENA))
                .containsExactly("16:00-18:00|2990", "17:00-19:00|2990", "18:00-20:00|2990", "19:00-21:00|2990");
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template where zone_id = ?::uuid "
                + "and price_clp = 2990 and status = 'ACTIVE'", Integer.class, LA_SERENA)).isEqualTo(28);
    }

    @Test
    void keepsDemoCapacitiesSmallAndEveryTemplateActiveForHomeDelivery() {
        assertThat(jdbc.queryForObject("select max(t.capacity) from catalog_slot_template t "
                + "join geo_delivery_zone z on z.id = t.zone_id where z.code not like 'LIDER_REGION_%'", Integer.class))
                .isLessThanOrEqualTo(10);
        assertThat(jdbc.queryForList("select distinct service_type || '|' || status from catalog_slot_template",
                String.class)).containsExactly("HOME_DELIVERY|ACTIVE");
    }

    @Test
    void includesAnotherPriceAndADayWithoutTemplatesInTheMetropolitanRegion() {
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template t "
                + "join geo_delivery_zone z on z.id = t.zone_id "
                + "where z.code <> 'LA_SERENA_COQUIMBO' and t.price_clp <> 2990", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForList("select z.code from geo_delivery_zone z where z.status = 'ACTIVE' "
                + "and z.code <> 'LA_SERENA_COQUIMBO' and (select count(distinct t.day_of_week) "
                + "from catalog_slot_template t where t.zone_id = z.id) < 7 "
                + "and z.code not like 'LIDER_REGION_%' order by z.code", String.class))
                .containsExactly("CHICUREO", "SANTIAGO_CENTRO");
    }

    @Test
    void givesEveryActiveNationalZoneOneAfternoonDeliveryTemplatePerWeekday() {
        assertThat(jdbc.queryForObject("select count(*) from geo_delivery_zone "
                + "where code like 'LIDER_REGION_%' and status = 'ACTIVE'", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template t "
                + "join geo_delivery_zone z on z.id = t.zone_id "
                + "where z.code like 'LIDER_REGION_%'", Integer.class)).isEqualTo(105);
        assertThat(jdbc.queryForList("select z.code from geo_delivery_zone z "
                + "left join catalog_slot_template t on t.zone_id = z.id "
                + "where z.code like 'LIDER_REGION_%' and z.status = 'ACTIVE' "
                + "group by z.code having count(t.id) <> 7 or count(distinct t.day_of_week) <> 7 "
                + "or count(t.id) filter (where t.start_time = time '16:00' and t.end_time = time '18:00' "
                + "and t.capacity = 20 and t.price_clp = 2990 and t.service_type = 'HOME_DELIVERY' "
                + "and t.status = 'ACTIVE') <> 7", String.class)).isEmpty();
    }

    @Test
    void offersNoTemplatesForTheInactiveZone() {
        assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template t "
                + "join geo_delivery_zone z on z.id = t.zone_id where z.status = 'INACTIVE'", Integer.class))
                .isZero();
    }
}
