package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.usecase.reservation.PurgeExpiredIdempotencyKeysUseCase;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Prueba el contrato HTTP de la clave de idempotencia contra PostgreSQL real. */
@IntegrationTest
@TestPropertySource(properties = {
        "app.reservation.idempotency.wait-timeout=PT1S",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0"})
class IdempotencyApiIT {

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private PurgeExpiredIdempotencyKeysUseCase purger;
    @MockitoSpyBean
    private SlotQueryPort slotQuery;

    private ReservationTestSlots slots;
    private ReservationInvariants invariants;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        invariants = new ReservationInvariants(jdbc);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(slotQuery);
        slots.cleanup();
    }

    @Test
    void replaysTheOriginalSuccessfulBodyAfterTheCapacityWasReleased() throws Exception {
        TestSlot slot = slots.createSlot(1);
        String customer = slots.newCustomerId();
        String key = "replay-" + UUID.randomUUID();
        var request = request(slot, slots.newOrderId());

        ExchangeResult first = post(customer, key, "correlation-first", request);
        ReservationSlotHoldDto hold = success(first, 201);
        assertThat(delete(customer, hold.getHoldId()).getStatus().value()).isEqualTo(204);

        ExchangeResult replay = post(customer, key, "correlation-replay", request);
        assertThat(replay.getStatus().value()).isEqualTo(201);
        assertThat(replay.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo("correlation-replay");
        assertThat(withoutCorrelation(first)).isEqualTo(withoutCorrelation(replay));
        invariants.assertConserved(slot.id());
    }

    @Test
    void rejectsAKeyReusedForADifferentSlotAndKeepsKeysScopedToEachCustomer() throws Exception {
        TestSlot firstSlot = slots.createSlot(3);
        TestSlot otherSlot = slots.slot().dayOffset(1).create();
        String sharedKey = "shared-" + UUID.randomUUID();
        String firstCustomer = slots.newCustomerId();
        String secondCustomer = slots.newCustomerId();

        success(post(firstCustomer, sharedKey, "key-first", request(firstSlot, slots.newOrderId())), 201);
        assertThat(problem(post(firstCustomer, sharedKey, "key-reused", request(otherSlot, slots.newOrderId())), 422)
                .getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        ReservationSlotHoldDto independent = success(post(secondCustomer, sharedKey, "key-other-customer",
                request(otherSlot, slots.newOrderId())), 201);
        assertThat(independent.getSlot().getSlotId()).isEqualTo(otherSlot.id());
        invariants.assertConserved(firstSlot.id(), otherSlot.id());
    }

    @Test
    void rollsBackAnUnexpectedFailureSoTheSameKeyCanBeRetried() throws Exception {
        TestSlot slot = slots.createSlot(1);
        String customer = slots.newCustomerId();
        String key = "retry-500-" + UUID.randomUUID();
        var request = request(slot, slots.newOrderId());
        Mockito.doThrow(new IllegalStateException("forced once")).doCallRealMethod()
                .when(slotQuery).findSlot(slot.id());

        assertThat(post(customer, key, "five-hundred", request).getStatus().value()).isEqualTo(500);
        ReservationSlotHoldDto retried = success(post(customer, key, "retry", request), 201);
        assertThat(retried.getSlot().getSlotId()).isEqualTo(slot.id());
        assertThat(countKeys(customer, key)).isEqualTo(1);
        invariants.assertConserved(slot.id());
    }

    @Test
    void replaysTheFixedSoldOutRejectionEvenAfterCapacityBecomesAvailable() throws Exception {
        TestSlot slot = slots.createSlot(1);
        ReservationSlotHoldDto owner = success(post(slots.newCustomerId(), "owner-" + UUID.randomUUID(), "owner",
                request(slot, slots.newOrderId())), 201);
        String customer = slots.newCustomerId();
        String key = "fixed-full-" + UUID.randomUUID();
        var request = request(slot, slots.newOrderId());

        ExchangeResult initial = post(customer, key, "full-first", request);
        assertThat(problem(initial, 409).getCode()).isEqualTo("SLOT_FULL");
        assertThat(delete(ownerCustomer(owner), owner.getHoldId()).getStatus().value()).isEqualTo(204);

        ExchangeResult replay = post(customer, key, "full-replay", request);
        assertThat(problem(replay, 409).getCode()).isEqualTo("SLOT_FULL");
        assertThat(withoutCorrelation(initial)).isEqualTo(withoutCorrelation(replay));
        invariants.assertConserved(slot.id());
    }

    @Test
    void purgesAnExpiredKeyAndProcessesTheSameKeyAsANewRequest() throws Exception {
        TestSlot first = slots.createSlot(2);
        TestSlot replacement = slots.slot().dayOffset(1).create();
        String customer = slots.newCustomerId();
        String key = "expired-" + UUID.randomUUID();
        ReservationSlotHoldDto old = success(post(customer, key, "expired-first", request(first, slots.newOrderId())), 201);
        assertThat(delete(customer, old.getHoldId()).getStatus().value()).isEqualTo(204);
        jdbc.update("update reservation_idempotency_key set expires_at = now() - interval '1 second' "
                + "where customer_id = ? and idempotency_key = ?", customer, key);

        assertThat(purger.purgeExpired()).isGreaterThanOrEqualTo(1);
        ReservationSlotHoldDto newHold = success(post(customer, key, "expired-new", request(replacement, slots.newOrderId())), 201);
        assertThat(newHold.getSlot().getSlotId()).isEqualTo(replacement.id());
        invariants.assertConserved(first.id(), replacement.id());
    }

    @Test
    void reportsAnInProgressKeyWithRetryAfterWhenAnotherTransactionRetainsIt() throws Exception {
        TestSlot slot = slots.createSlot(1);
        String customer = slots.newCustomerId();
        String key = "in-progress-" + UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("insert into reservation_idempotency_key "
                    + "(customer_id, idempotency_key, operation, request_fingerprint, expires_at) "
                    + "values (?, ?, 'HOLD_SLOT', repeat('a', 64), now() + interval '1 day')")) {
                statement.setString(1, customer);
                statement.setString(2, key);
                statement.executeUpdate();
            }

            ExchangeResult response = post(customer, key, "in-progress", request(slot, slots.newOrderId()));
            assertThat(response.getResponseHeaders().getFirst("Retry-After")).isEqualTo("1");
            assertThat(problem(response, 409).getCode()).isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");
            connection.rollback();
        }
        invariants.assertConserved(slot.id());
    }

    private String ownerCustomer(ReservationSlotHoldDto ignored) {
        // La respuesta no expone customerId; el único cliente propietario de este escenario queda en la tabla.
        return jdbc.queryForObject("select customer_id from reservation_slot_hold where id = ?", String.class, ignored.getHoldId());
    }

    private ReservationCreateHoldRequestDto request(TestSlot slot, String order) {
        return new ReservationCreateHoldRequestDto(slot.id(), order);
    }

    private ExchangeResult post(String customer, String key, String correlationId, ReservationCreateHoldRequestDto request)
            throws Exception {
        return client().post().uri("/api/v1/slot-holds")
                .header("X-Customer-Id", customer).header("Idempotency-Key", key).header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(jsonMapper.writeValueAsBytes(request)).exchange().returnResult();
    }

    private ExchangeResult delete(String customer, UUID holdId) {
        return client().delete().uri("/api/v1/slot-holds/" + holdId).header("X-Customer-Id", customer)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
    }

    private ReservationSlotHoldDto success(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        return jsonMapper.readValue(result.getResponseBodyContent(), ReservationSlotHoldDto.class);
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

    private int countKeys(String customer, String key) {
        return jdbc.queryForObject("select count(*) from reservation_idempotency_key where customer_id = ? and idempotency_key = ?",
                Integer.class, customer, key);
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
