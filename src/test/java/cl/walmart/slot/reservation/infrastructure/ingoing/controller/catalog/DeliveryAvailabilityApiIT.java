package cl.walmart.slot.reservation.infrastructure.ingoing.controller.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliveryDatesDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliverySlotsDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP de fechas y bloques de despacho (requisitos 5.1, 6.1 a 6.8, 7.1 a 7.9, 12.1, 12.2) contra
 * PostgreSQL real. El reloj queda fijo en 2035 para no chocar con las ventanas generadas con el reloj real; la
 * caché se deshabilita porque los datos se modifican por SQL (el servidor corre en otro hilo, así que los
 * cambios se deshacen explícitamente).
 */
@IntegrationTest
@TestPropertySource(properties = "app.catalog.availability-cache.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeliveryAvailabilityApiIT {

    private static final String CORRELATION_ID = "catalog-api-it";
    private static final Instant TODAY_MORNING = Instant.parse("2035-03-05T13:00:00Z"); // lunes 10:00 Santiago (UTC-3)
    private static final String SERENA_ZONE = "5f1c0e2a-0000-4000-8000-000000000401";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TODAY_MORNING, ZoneId.of("America/Santiago"));
        }
    }

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.setInstant(TODAY_MORNING);
    }

    @AfterEach
    void restoreData() {
        jdbc.update("delete from catalog_holiday where holiday_date = date '2035-03-08'");
        jdbc.update("update catalog_delivery_slot set reserved = 0 where delivery_date = date '2035-03-07'");
    }

    @AfterAll
    void removeGeneratedWindows() {
        jdbc.update("delete from catalog_delivery_slot where delivery_date >= date '2035-01-01'");
    }

    @Test
    void listsSevenLabelledDatesForACoveredAddress() throws Exception {
        ExchangeResult result = get("/api/v1/delivery-dates?comunaCode=04101&latitude=-29.9045&longitude=-71.2489");

        CatalogDeliveryDatesDto body = ok(result, CatalogDeliveryDatesDto.class);
        assertThat(body.getZone().getCode()).isEqualTo("LA_SERENA_COQUIMBO");
        assertThat(body.getComuna().getCode()).isEqualTo("04101");
        assertThat(body.getTimeZone().getValue()).isEqualTo("America/Santiago");
        assertThat(body.getDates()).hasSize(7);
        assertThat(body.getDates()).extracting(d -> d.getLabel())
                .containsExactly("Hoy", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");
        assertThat(body.getDates()).extracting(d -> d.getStatus().getValue()).containsOnly("AVAILABLE");
        assertThat(body.getDates().getFirst().getDate().toString()).isEqualTo("2035-03-05");
    }

    @Test
    void offersAfternoonDeliveryForMaculAndVinaWithinTheCalendarHorizon() throws Exception {
        for (String comunaCode : List.of("13118", "05109")) {
            CatalogDeliveryDatesDto dates = ok(get("/api/v1/delivery-dates?comunaCode=" + comunaCode),
                    CatalogDeliveryDatesDto.class);
            assertThat(dates.getComuna().getCode()).isEqualTo(comunaCode);
            assertThat(dates.getZone().getCode()).isEqualTo(comunaCode.equals("13118")
                    ? "LIDER_REGION_13" : "LIDER_REGION_05");
            assertThat(dates.getDates()).hasSize(7);
            assertThat(dates.getDates()).extracting(d -> d.getStatus().getValue()).containsOnly("AVAILABLE");

            CatalogDeliverySlotsDto slots = ok(get("/api/v1/delivery-dates/2035-03-06/slots?comunaCode="
                    + comunaCode), CatalogDeliverySlotsDto.class);
            assertThat(slots.getStatus().getValue()).isEqualTo("AVAILABLE");
            assertThat(slots.getSlots()).hasSize(1);
            assertThat(slots.getSlots().getFirst().getStart().toInstant().toString())
                    .isEqualTo("2035-03-06T19:00:00Z");
            assertThat(slots.getSlots().getFirst().getEnd().toInstant().toString())
                    .isEqualTo("2035-03-06T21:00:00Z");
            assertThat(slots.getSlots().getFirst().getPrice()).isEqualTo(2990L);
            assertThat(slots.getSlots().getFirst().getRemainingCapacity()).isEqualTo(20);
        }
    }

    @Test
    void reportsHolidayAsNoServiceAndEmptySlotList() throws Exception {
        jdbc.update("insert into catalog_holiday (holiday_date, name) values (date '2035-03-08', 'Feriado de prueba')");

        CatalogDeliveryDatesDto dates = ok(get("/api/v1/delivery-dates?comunaCode=04101"), CatalogDeliveryDatesDto.class);
        assertThat(dates.getDates().get(3).getStatus().getValue()).isEqualTo("NO_SERVICE");

        CatalogDeliverySlotsDto slots = ok(get("/api/v1/delivery-dates/2035-03-08/slots?comunaCode=04101"),
                CatalogDeliverySlotsDto.class);
        assertThat(slots.getStatus().getValue()).isEqualTo("NO_SERVICE");
        assertThat(slots.getSlots()).isEmpty();
    }

    @Test
    void reportsADayWithFilledCountersAsSoldOutInDatesAndSlots() throws Exception {
        jdbc.update("update catalog_delivery_slot set reserved = capacity where delivery_date = date '2035-03-07' and zone_id = ?::uuid", SERENA_ZONE);

        CatalogDeliveryDatesDto dates = ok(get("/api/v1/delivery-dates?comunaCode=04101"), CatalogDeliveryDatesDto.class);
        assertThat(dates.getDates().get(2).getStatus().getValue()).isEqualTo("SOLD_OUT");

        CatalogDeliverySlotsDto slots = ok(get("/api/v1/delivery-dates/2035-03-07/slots?comunaCode=04101"),
                CatalogDeliverySlotsDto.class);
        assertThat(slots.getStatus()).isEqualTo(dates.getDates().get(2).getStatus());
        assertThat(slots.getSlots()).hasSize(4);
        assertThat(slots.getSlots()).extracting(s -> s.getStatus().getValue()).containsOnly("SOLD_OUT");
        assertThat(slots.getSlots()).extracting(s -> s.getRemainingCapacity()).containsOnly(0);
    }

    @Test
    void listsOrderedSlotsWithPriceCapacityAndSantiagoOffset() throws Exception {
        ExchangeResult result = get("/api/v1/delivery-dates/2035-03-06/slots?comunaCode=04101");
        CatalogDeliverySlotsDto slots = ok(result, CatalogDeliverySlotsDto.class);

        assertThat(new String(result.getResponseBodyContent())).contains("\"start\":\"2035-03-06T16:00:00-03:00\"",
                "\"end\":\"2035-03-06T18:00:00-03:00\"");
        assertThat(slots.getLabel()).isEqualTo("Mar");
        assertThat(slots.getStatus().getValue()).isEqualTo("AVAILABLE");
        assertThat(slots.getSlots()).hasSize(4);
        assertThat(slots.getSlots()).extracting(s -> s.getPrice()).containsOnly(2990L);
        assertThat(slots.getSlots()).extracting(s -> s.getRemainingCapacity()).containsOnly(8);
        assertThat(slots.getSlots()).extracting(s -> s.getStart().toInstant().toString()).containsExactly(
                "2035-03-06T19:00:00Z", "2035-03-06T20:00:00Z", "2035-03-06T21:00:00Z", "2035-03-06T22:00:00Z");
        assertThat(slots.getSlots().getFirst().getEnd().toInstant().toString()).isEqualTo("2035-03-06T21:00:00Z");
    }

    @Test
    void marksCutoffPassedAndOmitsStartedSlotsAsTheClockAdvances() throws Exception {
        clock.setInstant(Instant.parse("2035-03-05T17:30:00Z")); // 14:30 Santiago: cierre de 16:00 (14:00) alcanzado
        CatalogDeliverySlotsDto afterCutoff = ok(get("/api/v1/delivery-dates/2035-03-05/slots?comunaCode=04101"),
                CatalogDeliverySlotsDto.class);
        assertThat(afterCutoff.getSlots()).extracting(s -> s.getStatus().getValue())
                .containsExactly("CUTOFF_PASSED", "AVAILABLE", "AVAILABLE", "AVAILABLE");

        clock.setInstant(Instant.parse("2035-03-05T19:30:00Z")); // 16:30 Santiago: el bloque de 16:00 ya inició
        CatalogDeliverySlotsDto afterStart = ok(get("/api/v1/delivery-dates/2035-03-05/slots?comunaCode=04101"),
                CatalogDeliverySlotsDto.class);
        assertThat(afterStart.getSlots()).hasSize(3);
        assertThat(afterStart.getSlots().getFirst().getStart().toInstant().toString()).isEqualTo("2035-03-05T20:00:00Z");
    }

    @Test
    void rejectsMalformedAndOutOfRangeParameters() throws Exception {
        for (String uri : List.of(
                "/api/v1/delivery-dates?comunaCode=1310",
                "/api/v1/delivery-dates?comunaCode=abcde",
                "/api/v1/delivery-dates?comunaCode=13101&latitude=91&longitude=-70",
                "/api/v1/delivery-dates?comunaCode=13101&latitude=-33&longitude=181",
                "/api/v1/delivery-dates/not-a-date/slots?comunaCode=13101")) {
            assertThat(problem(get(uri), 400).getCode()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void rejectsADateOutsideTheDisplayPeriodWithTheAllowedRange() throws Exception {
        ExchangeResult result = get("/api/v1/delivery-dates/2035-03-12/slots?comunaCode=04101");

        assertThat(problem(result, 400).getCode()).isEqualTo("DELIVERY_DATE_OUT_OF_RANGE");
        JsonNode json = jsonMapper.readTree(result.getResponseBodyContent());
        assertThat(json.get("date").asString()).isEqualTo("2035-03-12");
        assertThat(json.get("minDate").asString()).isEqualTo("2035-03-05");
        assertThat(json.get("maxDate").asString()).isEqualTo("2035-03-11");
        assertThat(problem(get("/api/v1/delivery-dates/2035-03-04/slots?comunaCode=04101"), 400).getCode())
                .isEqualTo("DELIVERY_DATE_OUT_OF_RANGE");
    }

    @Test
    void answersAddressErrorsIdenticallyForDatesAndSlots() throws Exception {
        for (String suffix : List.of("", "/2035-03-06/slots")) {
            String base = "/api/v1/delivery-dates" + suffix;
            assertThat(problem(get(base + "?comunaCode=99999"), 400).getCode()).isEqualTo("UNKNOWN_COMUNA");
            assertThat(problem(get(base + "?comunaCode=13101&latitude=-33.4"), 400).getCode())
                    .isEqualTo("INCOMPLETE_COORDINATES");
            assertNotCovered(get(base + "?comunaCode=13119"), "ZONE_INACTIVE", "13119");
            assertNotCovered(get(base + "?comunaCode=11101"), "COMUNA_NOT_SERVED", "11101");
        }
    }

    @Test
    void answersNotAcceptableAsProblemDetails() throws Exception {
        ExchangeResult result = client().get().uri("/api/v1/delivery-dates?comunaCode=04101")
                .header("X-Correlation-Id", CORRELATION_ID).accept(MediaType.APPLICATION_XML).exchange().returnResult();

        assertThat(problem(result, 406).getCode()).isEqualTo("NOT_ACCEPTABLE");
    }

    private void assertNotCovered(ExchangeResult result, String reason, String comunaCode) throws Exception {
        assertThat(problem(result, 422).getCode()).isEqualTo("ADDRESS_NOT_COVERED");
        JsonNode json = jsonMapper.readTree(result.getResponseBodyContent());
        assertThat(json.get("reason").asString()).isEqualTo(reason);
        assertThat(json.get("comunaCode").asString()).isEqualTo(comunaCode);
    }

    private <T> T ok(ExchangeResult result, Class<T> type) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        return jsonMapper.readValue(result.getResponseBodyContent(), type);
    }

    private ProblemDto problem(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        ProblemDto problem = jsonMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(problem.getCorrelationId()).isEqualTo(CORRELATION_ID);
        return problem;
    }

    private ExchangeResult get(String uri) {
        return client().get().uri(uri).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
