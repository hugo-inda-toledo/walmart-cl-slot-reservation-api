package cl.walmart.slot.reservation.integration.catalog.acceleration;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.port.catalog.AvailabilityCacheInvalidationPort;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliveryDatesDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliverySlotsDto;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escenario compartido de los tres modos de la caché de disponibilidad (activa, deshabilitada por configuración
 * y Valkey detenido). Cada modo debe observar exactamente las mismas respuestas de fechas y bloques (requisitos
 * 4.4, 8.4, 9.1, 9.2, 9.4, 12.5): la caché es solo un acelerador y no cambia la corrección.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AvailabilityAccelerationScenario {

    static final UUID SERENA_ZONE = UUID.fromString("5f1c0e2a-0000-4000-8000-000000000401");
    private static final LocalDate TODAY = LocalDate.of(2036, 3, 5);
    private static final LocalDate SOLD_OUT_DAY = TODAY.plusDays(2);

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected AvailabilityCacheInvalidationPort invalidation;
    @Autowired
    private GenerateSlotsUseCase generation;

    @BeforeAll
    void generateWindowsForTheFixedClock() {
        clock.setInstant(AccelerationClockConfiguration.TODAY_MORNING);
        generation.generate();
    }

    @BeforeEach
    void resetState() {
        restoreCounters();
        clock.setInstant(AccelerationClockConfiguration.TODAY_MORNING);
    }

    @AfterAll
    void removeGeneratedWindows() {
        jdbc.update("delete from catalog_delivery_slot where delivery_date >= date '2036-01-01'");
    }

    /** Devuelve los contadores a 0 y avisa la invalidación de cada fecha del período consultable. */
    protected void restoreCounters() {
        jdbc.update("update catalog_delivery_slot set reserved = 0, held = 0 where zone_id = ? and delivery_date >= ?",
                SERENA_ZONE, TODAY);
        for (int offset = 0; offset < 7; offset++) {
            invalidation.invalidate(SERENA_ZONE, TODAY.plusDays(offset));
        }
    }

    protected void fillSoldOutDayCountersBySql() {
        jdbc.update("update catalog_delivery_slot set reserved = capacity where zone_id = ? and delivery_date = ?",
                SERENA_ZONE, SOLD_OUT_DAY);
    }

    protected void notifySoldOutDayChange() {
        invalidation.invalidate(SERENA_ZONE, SOLD_OUT_DAY);
    }

    /** Recorre el mismo flujo en cualquier modo y devuelve lo observado para compararlo con la expectativa fija. */
    protected List<String> observeScenario() throws Exception {
        List<String> observed = new ArrayList<>();
        observed.add("dates@10:00 " + dates());
        fillSoldOutDayCountersBySql();
        notifySoldOutDayChange();
        observed.add("dates@10:00 after-change " + dates());
        observed.add("slots-sold-out " + slots(SOLD_OUT_DAY));
        observed.add("slots-today@10:00 " + slots(TODAY));
        clock.setInstant(Instant.parse("2036-03-05T17:30:00Z")); // 14:30 Santiago: cierre (14:00) del bloque de las 16:00 alcanzado
        observed.add("slots-today@14:30 " + slots(TODAY));
        return observed;
    }

    protected static List<String> expectedObservations() {
        String sevenAvailable = "[2036-03-05=AVAILABLE, 2036-03-06=AVAILABLE, 2036-03-07=AVAILABLE, 2036-03-08=AVAILABLE,"
                + " 2036-03-09=AVAILABLE, 2036-03-10=AVAILABLE, 2036-03-11=AVAILABLE]";
        String withSoldOut = sevenAvailable.replace("2036-03-07=AVAILABLE", "2036-03-07=SOLD_OUT");
        String available = "AVAILABLE/8/2990";
        return List.of(
                "dates@10:00 " + sevenAvailable,
                "dates@10:00 after-change " + withSoldOut,
                "slots-sold-out SOLD_OUT [SOLD_OUT/0/2990, SOLD_OUT/0/2990, SOLD_OUT/0/2990, SOLD_OUT/0/2990]",
                "slots-today@10:00 AVAILABLE [" + String.join(", ", available, available, available, available) + "]",
                "slots-today@14:30 AVAILABLE [CUTOFF_PASSED/8/2990, " + String.join(", ", available, available, available) + "]");
    }

    protected void assertScenarioMatchesTheExpectedObservations() throws Exception {
        assertThat(observeScenario()).containsExactlyElementsOf(expectedObservations());
    }

    protected String dates() throws Exception {
        CatalogDeliveryDatesDto body = jsonMapper.readValue(get("/api/v1/delivery-dates?comunaCode=04101"),
                CatalogDeliveryDatesDto.class);
        return body.getDates().stream().map(d -> d.getDate() + "=" + d.getStatus().getValue()).toList().toString();
    }

    protected String slots(LocalDate date) throws Exception {
        CatalogDeliverySlotsDto body = jsonMapper.readValue(
                get("/api/v1/delivery-dates/" + date + "/slots?comunaCode=04101"), CatalogDeliverySlotsDto.class);
        return body.getStatus().getValue() + " " + body.getSlots().stream()
                .map(s -> s.getStatus().getValue() + "/" + s.getRemainingCapacity() + "/" + s.getPrice()).toList();
    }

    private byte[] get(String uri) {
        ExchangeResult result = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build().get()
                .uri(uri).accept(MediaType.APPLICATION_JSON).exchange().returnResult();
        assertThat(result.getStatus().value()).as(uri).isEqualTo(200);
        return result.getResponseBodyContent();
    }
}
