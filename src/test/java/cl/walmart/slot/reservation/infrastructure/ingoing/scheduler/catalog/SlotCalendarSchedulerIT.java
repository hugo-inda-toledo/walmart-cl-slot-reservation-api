package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Generación al arrancar (requisitos 2.3, 2.4, 11.5 a 11.7): con el reloj real y el modo demo deshabilitado
 * (default de tests) existen ventanas para hoy y el horizonte de las zonas activas, ninguna para Maipú y
 * ninguna capacidad difiere de su plantilla.
 */
@IntegrationTest
class SlotCalendarSchedulerIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void generatesTodayAndTheHorizonForActiveZonesOnly() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));

        // La Serena atiende los siete días: cada fecha del horizonte tiene ventanas salvo los feriados nacionales.
        Integer generatedDates = jdbc.queryForObject("""
                select count(distinct delivery_date) from catalog_delivery_slot
                where zone_id = '5f1c0e2a-0000-4000-8000-000000000401'::uuid and delivery_date between ? and ?""",
                Integer.class, today, today.plusDays(13));
        Integer holidays = jdbc.queryForObject("""
                select count(*) from catalog_holiday where zone_id is null and holiday_date between ? and ?""",
                Integer.class, today, today.plusDays(13));
        Integer maipu = jdbc.queryForObject("""
                select count(*) from catalog_delivery_slot s join geo_delivery_zone z on z.id = s.zone_id
                where z.code = 'MAIPU'""", Integer.class);
        Integer inactiveZones = jdbc.queryForObject("""
                select count(*) from catalog_delivery_slot s join geo_delivery_zone z on z.id = s.zone_id
                where z.status <> 'ACTIVE'""", Integer.class);

        assertThat(generatedDates).isEqualTo(14 - holidays);
        assertThat(maipu).isZero();
        assertThat(inactiveZones).isZero();
    }

    @Test
    void leavesEveryCapacityEqualToItsTemplateWhenDemoModeIsDisabled() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));

        Integer differing = jdbc.queryForObject("""
                select count(*) from catalog_delivery_slot s join catalog_slot_template t on t.id = s.template_id
                where s.delivery_date between ? and ? and s.capacity <> t.capacity""",
                Integer.class, today, today.plusDays(13));

        assertThat(differing).isZero();
    }
}
