package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Modo demo habilitado (requisitos 2.9, 2.10, 11.5): tras el arranque, el día agotado demo (hoy + offset) de La
 * Serena queda con capacidad igual a lo tomado y se publica como {@code SOLD_OUT}; los demás días no cambian.
 * Reloj fijo en 2036 para no alterar las ventanas del reloj real que verifican otras clases.
 */
@IntegrationTest
@TestPropertySource(properties = {"app.catalog.demo.enabled=true", "app.catalog.availability-cache.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlotCalendarSchedulerDemoModeIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableClock demoClock() {
            return new MutableClock(Instant.parse("2036-03-03T13:00:00Z"), ZoneId.of("America/Santiago"));
        }
    }

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;

    @AfterAll
    void removeGeneratedWindows() {
        jdbc.update("delete from catalog_delivery_slot where delivery_date >= date '2036-01-01'");
    }

    @Test
    void closesTheDemoDayOfLaSerenaAndReportsItAsSoldOut() throws Exception {
        String body = new String(RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build().get()
                .uri("/api/v1/delivery-dates?comunaCode=04101").accept(MediaType.APPLICATION_JSON)
                .exchange().returnResult().getResponseBodyContent());
        var dates = jsonMapper.readTree(body).get("dates");

        assertThat(dates.get(0).get("status").asString()).isEqualTo("AVAILABLE");
        assertThat(dates.get(1).get("status").asString()).isEqualTo("AVAILABLE");
        assertThat(dates.get(2).get("date").asString()).isEqualTo("2036-03-05");
        assertThat(dates.get(2).get("status").asString()).isEqualTo("SOLD_OUT");
        assertThat(dates.get(3).get("status").asString()).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("""
                select count(*) from catalog_delivery_slot s join catalog_slot_template t on t.id = s.template_id
                where s.delivery_date = date '2036-03-05' and s.zone_id = '5f1c0e2a-0000-4000-8000-000000000401'::uuid
                  and s.capacity <> s.reserved + s.held""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from catalog_delivery_slot s join catalog_slot_template t on t.id = s.template_id
                where s.delivery_date = date '2036-03-06' and s.capacity <> t.capacity""", Integer.class)).isZero();
    }
}
