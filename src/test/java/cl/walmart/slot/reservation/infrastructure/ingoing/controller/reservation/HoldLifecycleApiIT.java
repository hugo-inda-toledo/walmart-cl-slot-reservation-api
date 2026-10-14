package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.port.catalog.AvailabilityCacheInvalidationPort;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationHoldStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
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

/**
 * Contrato HTTP de creación y consulta de retenciones sobre PostgreSQL real. Cada escenario conserva los
 * contadores y deserializa respuestas con los DTO generados por OpenAPI.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0"})
class HoldLifecycleApiIT {

    private static final String CORRELATION_ID = "reservation-hold-api-it";

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoSpyBean
    private AvailabilityCacheInvalidationPort invalidation;

    private ReservationTestSlots slots;
    private ReservationInvariants invariants;
    private ListAppender<ILoggingEvent> logs;
    private Logger errorLogger;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        invariants = new ReservationInvariants(jdbc);
        logs = new ListAppender<>();
        logs.start();
        errorLogger = (Logger) LoggerFactory.getLogger(
                "cl.walmart.slot.reservation.cmd.exception.GlobalExceptionHandler");
        errorLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        errorLogger.detachAppender(logs);
        Mockito.reset(invalidation);
        slots.cleanup();
    }

    @Test
    void createsAndGetsAHoldWithDatabaseTtlLocationGeneratedDtoAndAfterCommitInvalidation() throws Exception {
        TestSlot slot = slots.createSlot(2);
        String customer = slots.newCustomerId();
        String order = slots.newOrderId();
        var before = slots.dbNow();

        ExchangeResult created = post(customer, "create-1", new ReservationCreateHoldRequestDto(slot.id(), order));
        ReservationSlotHoldDto hold = success(created, 201, ReservationSlotHoldDto.class);

        assertThat(created.getResponseHeaders().getLocation()).hasToString("/api/v1/slot-holds/" + hold.getHoldId());
        assertThat(hold.getOrderId()).isEqualTo(order);
        assertThat(hold.getStatus()).isEqualTo(ReservationHoldStatusDto.ACTIVE);
        assertThat(hold.getSlot().getSlotId()).isEqualTo(slot.id());
        assertThat(hold.getSlot().getTimeZone().getValue()).isEqualTo("America/Santiago");
        assertThat(hold.getExpiresAt().toInstant()).isBetween(before.plus(Duration.ofMinutes(9)), before.plus(Duration.ofMinutes(11)));
        assertThat(hold.getCreatedAt().toInstant()).isBeforeOrEqualTo(hold.getExpiresAt().toInstant());
        assertThat(success(get(customer, hold.getHoldId()), 200, ReservationSlotHoldDto.class)).isEqualTo(hold);
        invariants.assertConserved(slot.id());

        ArgumentCaptor<UUID> zone = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<java.time.LocalDate> date = ArgumentCaptor.forClass(java.time.LocalDate.class);
        Mockito.verify(invalidation).invalidate(zone.capture(), date.capture());
        assertThat(zone.getValue()).isEqualTo(slot.zoneId());
        assertThat(date.getValue()).isEqualTo(slot.deliveryDate());
    }

    @Test
    void rejectsInvalidRequestIdentityAndMissingIdempotencyKeyWithoutCallingAfterCommitInvalidation() throws Exception {
        TestSlot slot = slots.createSlot(1);
        String validCustomer = slots.newCustomerId();
        var request = new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId());

        assertThat(problem(post(null, "key", request), 400).getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem(post("invalid customer", "key", request), 400).getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem(post(validCustomer, null, request), 400).getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(problem(post(validCustomer, "invalid-body", new ReservationCreateHoldRequestDto(slot.id(), "bad order!")), 400)
                .getCode()).isEqualTo("VALIDATION_ERROR");
        invariants.assertConserved(slot.id());
        Mockito.verifyNoInteractions(invalidation);
    }

    @Test
    void rejectsNotFoundNoServiceAndCutoffWithoutChangingCounters() throws Exception {
        String customer = slots.newCustomerId();
        assertThat(problem(post(customer, "missing", new ReservationCreateHoldRequestDto(UUID.randomUUID(), slots.newOrderId())), 404)
                .getCode()).isEqualTo("SLOT_NOT_FOUND");

        TestSlot noService = slots.slot().dayOffset(1).create();
        slots.blockZoneOn(1);
        assertThat(problem(post(customer, "no-service", new ReservationCreateHoldRequestDto(noService.id(), slots.newOrderId())), 422)
                .getCode()).isEqualTo("SLOT_NO_SERVICE");

        TestSlot closed = slots.slot().cutoffFromDbNow(Duration.ofSeconds(-1)).create();
        assertThat(problem(post(customer, "closed", new ReservationCreateHoldRequestDto(closed.id(), slots.newOrderId())), 422)
                .getCode()).isEqualTo("SLOT_CUTOFF_PASSED");
        invariants.assertConserved(noService.id(), closed.id());
        Mockito.verifyNoInteractions(invalidation);
    }

    @Test
    void reportsFullActiveAndForeignHoldsWithoutLeakingOrBreakingCountersAndLogsOnlySafeData() throws Exception {
        TestSlot full = slots.createSlot(1);
        TestSlot alternative = slots.slot().startingAt(java.time.LocalTime.of(13, 0), Duration.ofHours(2)).create();
        String owner = slots.newCustomerId();
        String ownerOrder = slots.newOrderId();
        ReservationSlotHoldDto held = success(post(owner, "owner-key", new ReservationCreateHoldRequestDto(full.id(), ownerOrder)),
                201, ReservationSlotHoldDto.class);
        Mockito.clearInvocations(invalidation);

        String contender = slots.newCustomerId();
        ProblemDto fullProblem = problem(post(contender, "full-key", new ReservationCreateHoldRequestDto(full.id(), slots.newOrderId())), 409);
        assertThat(fullProblem.getCode()).isEqualTo("SLOT_FULL");
        JsonNode fullJson = jsonMapper.readTree(lastBody());
        assertThat(fullJson.get("alternatives").isArray()).isTrue();
        assertThat(fullJson.get("alternatives").toString()).contains(alternative.id().toString());
        assertLoggedSafely("SLOT_FULL", contender, ownerOrder);

        assertThat(problem(post(owner, "active-key", new ReservationCreateHoldRequestDto(alternative.id(), ownerOrder)), 409)
                .getCode()).isEqualTo("ACTIVE_HOLD_EXISTS");
        assertThat(problem(get(contender, held.getHoldId()), 404).getCode()).isEqualTo("HOLD_NOT_FOUND");
        invariants.assertConserved(full.id(), alternative.id());
        Mockito.verifyNoInteractions(invalidation);
    }

    @Test
    void replacesAnExpiredHoldForTheSameOrderAndReportsEffectiveExpiredStatus() throws Exception {
        TestSlot slot = slots.createSlot(2);
        String customer = slots.newCustomerId();
        String order = slots.newOrderId();
        UUID expired = insertExpiredActiveHold(slot, customer, order);
        ExchangeResult beforeReplacement = get(customer, expired);
        assertThat(success(beforeReplacement, 200, ReservationSlotHoldDto.class).getStatus())
                .isEqualTo(ReservationHoldStatusDto.EXPIRED);

        ReservationSlotHoldDto replacement = success(post(customer, "replace-expired", new ReservationCreateHoldRequestDto(slot.id(), order)),
                201, ReservationSlotHoldDto.class);
        assertThat(replacement.getHoldId()).isNotEqualTo(expired);
        assertThat(statusOf(expired)).isEqualTo("EXPIRED");
        invariants.assertConserved(slot.id());
        Mockito.verify(invalidation, Mockito.times(2)).invalidate(slot.zoneId(), slot.deliveryDate());
    }

    @Test
    void releasesAnActiveHoldIdempotentlyAndRejectsReleaseAfterConfirmation() throws Exception {
        TestSlot slot = slots.createSlot(2);
        String customer = slots.newCustomerId();
        ReservationSlotHoldDto releasable = createHold(slot, customer);
        Mockito.clearInvocations(invalidation);

        noContent(delete(customer, releasable.getHoldId()));
        noContent(delete(customer, releasable.getHoldId()));
        invariants.assertConserved(slot.id());
        Mockito.verify(invalidation).invalidate(slot.zoneId(), slot.deliveryDate());

        ReservationSlotHoldDto confirmed = createHold(slot, customer);
        ReservationBookingDto booking = confirm(customer, confirmed.getHoldId(), "confirm-release");
        assertThat(problem(delete(customer, confirmed.getHoldId()), 409).getCode()).isEqualTo("HOLD_NOT_ACTIVE");
        assertThat(booking.getStatus()).isEqualTo(ReservationBookingStatusDto.CONFIRMED);
        invariants.assertConserved(slot.id());
    }

    @Test
    void confirmsHoldExposesReservationAndDistinguishesExpiredNotActiveAndForeignResources() throws Exception {
        TestSlot slot = slots.createSlot(3);
        String owner = slots.newCustomerId();
        ReservationSlotHoldDto hold = createHold(slot, owner);
        Mockito.clearInvocations(invalidation);

        ExchangeResult confirmedResponse = postConfirm(owner, hold.getHoldId(), "confirm-1");
        ReservationBookingDto booking = success(confirmedResponse, 201, ReservationBookingDto.class);
        assertThat(confirmedResponse.getResponseHeaders().getLocation())
                .hasToString("/api/v1/reservations/" + booking.getReservationId());
        assertThat(booking.getHoldId()).isEqualTo(hold.getHoldId());
        assertThat(booking.getStatus()).isEqualTo(ReservationBookingStatusDto.CONFIRMED);
        assertThat(success(get(owner, hold.getHoldId()), 200, ReservationSlotHoldDto.class).getReservationId())
                .isEqualTo(booking.getReservationId());
        assertThat(success(getReservation(owner, booking.getReservationId()), 200, ReservationBookingDto.class))
                .isEqualTo(booking);
        assertThat(problem(postConfirm(owner, hold.getHoldId(), "confirm-again"), 409).getCode())
                .isEqualTo("HOLD_NOT_ACTIVE");
        invariants.assertConserved(slot.id());
        Mockito.verify(invalidation).invalidate(slot.zoneId(), slot.deliveryDate());

        String foreignCustomer = slots.newCustomerId();
        assertThat(problem(get(foreignCustomer, hold.getHoldId()), 404).getCode()).isEqualTo("HOLD_NOT_FOUND");
        assertThat(problem(delete(foreignCustomer, hold.getHoldId()), 404).getCode()).isEqualTo("HOLD_NOT_FOUND");
        assertThat(problem(postConfirm(foreignCustomer, hold.getHoldId(), "foreign-confirm"), 404).getCode())
                .isEqualTo("HOLD_NOT_FOUND");
        assertThat(problem(getReservation(foreignCustomer, booking.getReservationId()), 404).getCode())
                .isEqualTo("RESERVATION_NOT_FOUND");
        assertThat(problem(postCancel(foreignCustomer, booking.getReservationId()), 404).getCode())
                .isEqualTo("RESERVATION_NOT_FOUND");

        ReservationSlotHoldDto expired = createHold(slot, owner);
        jdbc.update("update reservation_slot_hold set created_at = now() - interval '2 seconds', "
                + "expires_at = now() - interval '1 second' where id = ?", expired.getHoldId());
        Mockito.clearInvocations(invalidation);
        assertThat(problem(postConfirm(owner, expired.getHoldId(), "confirm-expired"), 409).getCode())
                .isEqualTo("HOLD_EXPIRED");
        invariants.assertConserved(slot.id());
        Mockito.verifyNoInteractions(invalidation);
    }

    @Test
    void cancelsReservationIdempotentlyAndRejectsCancellationAtCutoff() throws Exception {
        TestSlot slot = slots.createSlot(3);
        String customer = slots.newCustomerId();
        ReservationBookingDto booking = confirm(customer, createHold(slot, customer).getHoldId(), "cancel-confirm");
        Mockito.clearInvocations(invalidation);

        ReservationBookingDto cancelled = success(postCancel(customer, booking.getReservationId()), 200, ReservationBookingDto.class);
        assertThat(cancelled.getStatus()).isEqualTo(ReservationBookingStatusDto.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        assertThat(success(postCancel(customer, booking.getReservationId()), 200, ReservationBookingDto.class)).isEqualTo(cancelled);
        assertThat(success(getReservation(customer, booking.getReservationId()), 200, ReservationBookingDto.class)).isEqualTo(cancelled);
        invariants.assertConserved(slot.id());
        Mockito.verify(invalidation).invalidate(slot.zoneId(), slot.deliveryDate());

        ReservationBookingDto closedBooking = confirm(customer, createHold(slot, customer).getHoldId(), "closed-confirm");
        jdbc.update("update reservation_booking set cutoff_at = now() - interval '1 second' where id = ?",
                closedBooking.getReservationId());
        Mockito.clearInvocations(invalidation);
        assertThat(problem(postCancel(customer, closedBooking.getReservationId()), 422).getCode())
                .isEqualTo("RESERVATION_CANCELLATION_CLOSED");
        invariants.assertConserved(slot.id());
        Mockito.verifyNoInteractions(invalidation);
    }

    private ExchangeResult post(String customer, String key, ReservationCreateHoldRequestDto request) throws Exception {
        var specification = client().post().uri("/api/v1/slot-holds")
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
        if (customer != null) {
            specification.header("X-Customer-Id", customer);
        }
        if (key != null) {
            specification.header("Idempotency-Key", key);
        }
        ExchangeResult result = specification.body(jsonMapper.writeValueAsBytes(request)).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ExchangeResult get(String customer, UUID holdId) {
        ExchangeResult result = client().get().uri("/api/v1/slot-holds/" + holdId)
                .header("X-Customer-Id", customer).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ExchangeResult delete(String customer, UUID holdId) {
        ExchangeResult result = client().delete().uri("/api/v1/slot-holds/" + holdId)
                .header("X-Customer-Id", customer).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ReservationBookingDto confirm(String customer, UUID holdId, String key) throws Exception {
        return success(postConfirm(customer, holdId, key), 201, ReservationBookingDto.class);
    }

    private ExchangeResult postConfirm(String customer, UUID holdId, String key) {
        ExchangeResult result = client().post().uri("/api/v1/slot-holds/" + holdId + "/confirm")
                .header("X-Customer-Id", customer).header("Idempotency-Key", key)
                .header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ExchangeResult getReservation(String customer, UUID reservationId) {
        ExchangeResult result = client().get().uri("/api/v1/reservations/" + reservationId)
                .header("X-Customer-Id", customer).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ExchangeResult postCancel(String customer, UUID reservationId) {
        ExchangeResult result = client().post().uri("/api/v1/reservations/" + reservationId + "/cancel")
                .header("X-Customer-Id", customer).header("X-Correlation-Id", CORRELATION_ID)
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        lastResponse = result;
        return result;
    }

    private ExchangeResult lastResponse;

    private byte[] lastBody() {
        return lastResponse.getResponseBodyContent();
    }

    private <T> T success(ExchangeResult result, int status, Class<T> type) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        return jsonMapper.readValue(result.getResponseBodyContent(), type);
    }

    private void noContent(ExchangeResult result) {
        assertThat(result.getStatus().value()).isEqualTo(204);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        assertThat(result.getResponseBodyContent()).isEmpty();
    }

    private ProblemDto problem(ExchangeResult result, int status) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDto body = jsonMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(body.getCorrelationId()).isEqualTo(CORRELATION_ID);
        return body;
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private UUID insertExpiredActiveHold(TestSlot slot, String customer, String order) {
        UUID id = UUID.randomUUID();
        jdbc.update("update catalog_delivery_slot set held = held + 1 where id = ?", slot.id());
        jdbc.update("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                        + "start_at, end_at, cutoff_at, price_clp, status, created_at, expires_at) values "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now() - interval '1 day', now() - interval '1 second')",
                id, customer, order, slot.id(), slot.zoneId(), slot.deliveryDate(),
                OffsetDateTime.ofInstant(slot.startAt(), ZoneOffset.UTC), OffsetDateTime.ofInstant(slot.endAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.cutoffAt(), ZoneOffset.UTC), slot.priceClp());
        return id;
    }

    private ReservationSlotHoldDto createHold(TestSlot slot, String customer) throws Exception {
        return success(post(customer, "hold-" + UUID.randomUUID(),
                new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId())), 201, ReservationSlotHoldDto.class);
    }

    private String statusOf(UUID holdId) {
        return jdbc.queryForObject("select status from reservation_slot_hold where id = ?", String.class, holdId);
    }

    private void assertLoggedSafely(String code, String forbiddenCustomer, String forbiddenOrder) {
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("Domain error: code=" + code);
            assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", CORRELATION_ID);
            assertThat(event.getFormattedMessage()).doesNotContain(forbiddenCustomer, forbiddenOrder);
        });
    }
}
