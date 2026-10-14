package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.Application;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.support.ContainerImages;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La disponibilidad acelerada puede desaparecer tras el arranque: PostgreSQL conserva la corrección de reservas
 * y la idempotencia, mientras que el aviso de invalidación no altera una transacción ya confirmada.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationValkeyDownIT {

    private static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"));
    private static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse(ContainerImages.VALKEY))
            .withExposedPorts(6379);

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private JsonMapper jsonMapper;
    private int port;

    @BeforeAll
    void startApplicationWithOwnDependencies() {
        POSTGIS.start();
        VALKEY.start();
        context = Application.run(
                "--spring.profiles.active=test",
                "--server.port=0",
                "--spring.datasource.url=" + POSTGIS.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGIS.getUsername(),
                "--spring.datasource.password=" + POSTGIS.getPassword(),
                "--spring.data.redis.host=" + VALKEY.getHost(),
                "--spring.data.redis.port=" + VALKEY.getFirstMappedPort(),
                "--app.catalog.availability-cache.enabled=true");
        jdbc = context.getBean(JdbcTemplate.class);
        jsonMapper = context.getBean(JsonMapper.class);
        port = ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    void stopOwnContextAndDependencies() {
        if (context != null) {
            context.close();
        }
        if (VALKEY.isRunning()) {
            VALKEY.stop();
        }
        if (POSTGIS.isRunning()) {
            POSTGIS.stop();
        }
    }

    @Test
    void preservesReservationOutcomesAndCountersAfterValkeyStops() throws Exception {
        ReservationTestSlots slots = new ReservationTestSlots(jdbc);
        ReservationInvariants invariants = new ReservationInvariants(jdbc);
        try {
            VALKEY.stop();

            TestSlot lifecycleSlot = slots.createSlot(1);
            String lifecycleCustomer = slots.newCustomerId();
            String holdKey = "valkey-down-hold-" + UUID.randomUUID();
            ReservationCreateHoldRequestDto holdRequest = request(lifecycleSlot, slots.newOrderId());

            ExchangeResult created = postHold(lifecycleCustomer, holdKey, holdRequest);
            ReservationSlotHoldDto hold = success(created, 201, ReservationSlotHoldDto.class);
            ExchangeResult replay = postHold(lifecycleCustomer, holdKey, holdRequest);
            assertThat(replay.getStatus().value()).isEqualTo(201);
            assertThat(withoutCorrelation(replay)).isEqualTo(withoutCorrelation(created));

            ReservationBookingDto confirmed = success(confirm(lifecycleCustomer, hold.getHoldId(),
                    "valkey-down-confirm-" + UUID.randomUUID()), 201, ReservationBookingDto.class);
            assertThat(confirmed.getHoldId()).isEqualTo(hold.getHoldId());
            ReservationBookingDto cancelled = success(cancel(lifecycleCustomer, confirmed.getReservationId()), 200,
                    ReservationBookingDto.class);
            assertThat(cancelled.getStatus()).isEqualTo(ReservationBookingStatusDto.CANCELLED);

            TestSlot fullSlot = slots.slot().dayOffset(1).capacity(1).create();
            success(postHold(slots.newCustomerId(), "valkey-down-owner-" + UUID.randomUUID(),
                    request(fullSlot, slots.newOrderId())), 201, ReservationSlotHoldDto.class);
            ProblemDto soldOut = problem(postHold(slots.newCustomerId(), "valkey-down-full-" + UUID.randomUUID(),
                    request(fullSlot, slots.newOrderId())), 409);
            assertThat(soldOut.getCode()).isEqualTo("SLOT_FULL");

            invariants.assertConserved(lifecycleSlot.id(), fullSlot.id());
        } finally {
            slots.cleanup();
        }
    }

    private ReservationCreateHoldRequestDto request(TestSlot slot, String orderId) {
        return new ReservationCreateHoldRequestDto(slot.id(), orderId);
    }

    private ExchangeResult postHold(String customer, String key, ReservationCreateHoldRequestDto request) throws Exception {
        return client().post().uri("/api/v1/slot-holds")
                .header("X-Customer-Id", customer).header("Idempotency-Key", key)
                .header("X-Correlation-Id", "valkey-down-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(jsonMapper.writeValueAsBytes(request)).exchange().returnResult();
    }

    private ExchangeResult confirm(String customer, UUID holdId, String key) {
        return client().post().uri("/api/v1/slot-holds/" + holdId + "/confirm")
                .header("X-Customer-Id", customer).header("Idempotency-Key", key)
                .header("X-Correlation-Id", "valkey-down-" + UUID.randomUUID())
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
    }

    private ExchangeResult cancel(String customer, UUID reservationId) {
        return client().post().uri("/api/v1/reservations/" + reservationId + "/cancel")
                .header("X-Customer-Id", customer).header("X-Correlation-Id", "valkey-down-" + UUID.randomUUID())
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
    }

    private <T> T success(ExchangeResult result, int status, Class<T> type) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        return jsonMapper.readValue(result.getResponseBodyContent(), type);
    }

    private ProblemDto problem(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        return jsonMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
    }

    private JsonNode withoutCorrelation(ExchangeResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(result.getResponseBodyContent());
        if (body instanceof tools.jackson.databind.node.ObjectNode object) {
            object.remove("correlationId");
        }
        return body;
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
