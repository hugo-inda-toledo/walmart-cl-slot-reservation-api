package cl.walmart.slot.reservation.integration.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
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

/** Carrera contra PostgreSQL entre confirmar y vencer una retención justo en el borde temporal. */
@IntegrationTest
class ConfirmVersusExpiryRaceIT {

    private static final int ITERATIONS = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private ReleaseExpiredHoldsUseCase releaseExpiredHolds;

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
    void givesExactlyOneWinnerToConfirmationOrExpiryInEachOfFiftyRaces() throws Exception {
        TestSlot slot = slots.createSlot(ITERATIONS + 5);
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            int race = iteration;
            String customer = slots.newCustomerId();
            ReservationSlotHoldDto hold = createHold(slot, customer);
            // Oscila pocos milisegundos alrededor del límite; el cero conserva una carrera real con clock_timestamp().
            int jitterMillis = switch (iteration % 3) { case 0 -> -2; case 1 -> 0; default -> -1; };
            jdbc.update("update reservation_slot_hold set expires_at = clock_timestamp() "
                    + "+ make_interval(secs => ? / 1000.0) where id = ?", jitterMillis, hold.getHoldId());

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<ExchangeResult> confirmation = executor.submit(() -> {
                    ready.countDown();
                    await(start, "confirmación y vencimiento");
                    return confirm(customer, hold.getHoldId(), "confirm-race-" + race);
                });
                Future<Integer> expiry = executor.submit(() -> {
                    ready.countDown();
                    await(start, "confirmación y vencimiento");
                    return releaseExpiredHolds.releaseExpired();
                });
                await(ready, "los dos corredores");
                start.countDown();
                int confirmationStatus = confirmation.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).getStatus().value();
                int expired = expiry.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

                assertThat(confirmationStatus).isIn(201, 409);
                assertThat((confirmationStatus == 201 ? 1 : 0) + expired)
                        .as("un único ganador en iteración %s", iteration).isEqualTo(1);
                invariants.assertConserved(slot.id());
            } finally {
                start.countDown();
                shutdown(executor);
            }
        }
    }

    private ReservationSlotHoldDto createHold(TestSlot slot, String customer) throws Exception {
        ExchangeResult result = client().post().uri("/api/v1/slot-holds").header("X-Customer-Id", customer)
                .header("Idempotency-Key", "create-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsBytes(new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId())))
                .exchange().returnResult();
        assertThat(result.getStatus().value()).isEqualTo(201);
        return jsonMapper.readValue(result.getResponseBodyContent(), ReservationSlotHoldDto.class);
    }

    private ExchangeResult confirm(String customer, UUID holdId, String key) {
        return client().post().uri("/api/v1/slot-holds/" + holdId + "/confirm").header("X-Customer-Id", customer)
                .header("Idempotency-Key", key).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .exchange().returnResult();
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
}
