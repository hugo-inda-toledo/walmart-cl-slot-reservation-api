package cl.walmart.slot.reservation.integration.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/** Las guardas SQL hacen idempotentes las oleadas concurrentes de liberación y cancelación. */
@IntegrationTest
class ConcurrentReleaseAndCancelIT {

    private static final int REQUESTS = 20;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;

    private ReservationTestSlots slots;
    private ReservationInvariants invariants;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        invariants = new ReservationInvariants(jdbc);
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    void decrementsHeldOnlyOnceWhenTwentyClientsReleaseTheSameHold() throws Exception {
        TestSlot slot = slots.createSlot(2);
        String customer = slots.newCustomerId();
        ReservationSlotHoldDto hold = createHold(slot, customer);

        List<ExchangeResult> responses = concurrently(REQUESTS,
                () -> client().delete().uri("/api/v1/slot-holds/" + hold.getHoldId()).header("X-Customer-Id", customer)
                        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult());

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(204));
        assertThat(jdbc.queryForObject("select held from catalog_delivery_slot where id = ?", Integer.class, slot.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reservation_slot_hold where id = ? and status = 'RELEASED'",
                Integer.class, hold.getHoldId())).isEqualTo(1);
        invariants.assertConserved(slot.id());
    }

    @Test
    void decrementsReservedOnlyOnceWhenTwentyClientsCancelTheSameReservation() throws Exception {
        TestSlot slot = slots.createSlot(2);
        String customer = slots.newCustomerId();
        ReservationBookingDto booking = confirm(customer, createHold(slot, customer).getHoldId());

        List<ExchangeResult> responses = concurrently(REQUESTS,
                () -> client().post().uri("/api/v1/reservations/" + booking.getReservationId() + "/cancel")
                        .header("X-Customer-Id", customer).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                        .exchange().returnResult());

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(200));
        assertThat(jdbc.queryForObject("select reserved from catalog_delivery_slot where id = ?", Integer.class, slot.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reservation_booking where id = ? and status = 'CANCELLED'",
                Integer.class, booking.getReservationId())).isEqualTo(1);
        invariants.assertConserved(slot.id());
    }

    private ReservationSlotHoldDto createHold(TestSlot slot, String customer) throws Exception {
        ExchangeResult result = client().post().uri("/api/v1/slot-holds").header("X-Customer-Id", customer)
                .header("Idempotency-Key", "create-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsBytes(new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId())))
                .exchange().returnResult();
        assertThat(result.getStatus().value()).isEqualTo(201);
        return jsonMapper.readValue(result.getResponseBodyContent(), ReservationSlotHoldDto.class);
    }

    private ReservationBookingDto confirm(String customer, UUID holdId) throws Exception {
        ExchangeResult result = client().post().uri("/api/v1/slot-holds/" + holdId + "/confirm")
                .header("X-Customer-Id", customer).header("Idempotency-Key", "confirm-" + UUID.randomUUID())
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON).exchange().returnResult();
        assertThat(result.getStatus().value()).isEqualTo(201);
        return jsonMapper.readValue(result.getResponseBodyContent(), ReservationBookingDto.class);
    }

    private List<ExchangeResult> concurrently(int count, Request request) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<ExchangeResult>> futures = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start, "las solicitudes simultáneas");
                    return request.execute();
                }));
            }
            await(ready, "las solicitudes preparadas");
            start.countDown();
            List<ExchangeResult> results = new ArrayList<>(count);
            for (Future<ExchangeResult> future : futures) {
                results.add(future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static void await(CountDownLatch latch, String awaited) throws InterruptedException {
        assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("espera de %s", awaited).isTrue();
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface
    private interface Request {
        ExchangeResult execute();
    }
}
