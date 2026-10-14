package cl.walmart.slot.reservation.integration.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants.SlotCounters;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
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

/** Verifica por HTTP que los reintentos simultáneos de una clave se resuelven como una sola retención. */
@IntegrationTest
class IdempotentRetryRaceIT {

    private static final int RETRIES = 50;
    private static final Duration GATE_TIMEOUT = Duration.ofSeconds(30);

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
    void returnsTheSameCreatedHoldToFiftyConcurrentRetries() throws Exception {
        TestSlot slot = slots.createSlot(5);
        String customer = slots.newCustomerId();
        String key = "idempotent-race-" + UUID.randomUUID();
        ReservationCreateHoldRequestDto request = new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId());
        CountDownLatch ready = new CountDownLatch(RETRIES);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(RETRIES);

        try {
            List<Future<ExchangeResult>> responses = new ArrayList<>(RETRIES);
            for (int index = 0; index < RETRIES; index++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    await(start, "la puerta común de salida");
                    return post(customer, key, request);
                }));
            }

            await(ready, "los 50 reintentos preparados");
            start.countDown();
            List<ExchangeResult> completed = awaitAll(responses);
            assertThat(completed).allSatisfy(response -> assertThat(response.getStatus().value()).isEqualTo(201));

            List<ReservationSlotHoldDto> holds = new ArrayList<>(RETRIES);
            for (ExchangeResult response : completed) {
                holds.add(jsonMapper.readValue(response.getResponseBodyContent(), ReservationSlotHoldDto.class));
            }
            assertThat(holds).allSatisfy(hold -> assertThat(hold).isEqualTo(holds.getFirst()));
            assertThat(holds).extracting(ReservationSlotHoldDto::getHoldId).containsOnly(holds.getFirst().getHoldId());

            SlotCounters counters = invariants.countersOf(List.of(slot.id())).getFirst();
            assertThat(counters.held()).isEqualTo(1);
            assertThat(counters.activeHolds()).isEqualTo(1);
            invariants.assertConserved(slot.id());
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }

    private ExchangeResult post(String customer, String key, ReservationCreateHoldRequestDto request) throws Exception {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build().post().uri("/api/v1/slot-holds")
                .header("X-Customer-Id", customer).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
                .body(jsonMapper.writeValueAsBytes(request)).exchange().returnResult();
    }

    private static <T> List<T> awaitAll(List<Future<T>> futures) throws Exception {
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            results.add(future.get(GATE_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        }
        return results;
    }

    private static void await(CountDownLatch latch, String awaited) throws InterruptedException {
        assertThat(latch.await(GATE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("espera de %s", awaited).isTrue();
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(GATE_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }
}
