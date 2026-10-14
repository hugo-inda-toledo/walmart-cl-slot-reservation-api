package cl.walmart.slot.reservation.integration.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants.SlotCounters;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
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

/** Verifica por HTTP que PostgreSQL conserva la capacidad ante una salida simultánea de 200 clientes. */
@IntegrationTest
class CapacityRaceIT {

    private static final int CLIENTS = 200;
    private static final int CAPACITY = 5;
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
    void admitsExactlyCapacityWhenTwoHundredDistinctCustomersStartTogether() throws Exception {
        TestSlot slot = slots.createSlot(CAPACITY);
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS);
        long startedAt = System.nanoTime();

        try {
            List<Future<ExchangeResult>> responses = new ArrayList<>(CLIENTS);
            for (int index = 0; index < CLIENTS; index++) {
                String customer = slots.newCustomerId();
                String key = "capacity-race-" + UUID.randomUUID();
                ReservationCreateHoldRequestDto request = new ReservationCreateHoldRequestDto(slot.id(), slots.newOrderId());
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    await(start, "la puerta común de salida");
                    return post(customer, key, request);
                }));
            }

            await(ready, "los 200 clientes preparados");
            start.countDown();
            List<ExchangeResult> completed = awaitAll(responses);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            System.out.printf("CapacityRaceIT: %d clientes completaron en %d ms%n", CLIENTS, durationMillis);

            assertThat(completed).filteredOn(response -> response.getStatus().value() == 201).hasSize(CAPACITY);
            List<ExchangeResult> full = completed.stream().filter(response -> response.getStatus().value() == 409).toList();
            assertThat(full).hasSize(CLIENTS - CAPACITY);
            for (ExchangeResult response : full) {
                assertThat(jsonMapper.readValue(response.getResponseBodyContent(), ProblemDto.class).getCode())
                        .isEqualTo("SLOT_FULL");
            }

            SlotCounters counters = invariants.countersOf(List.of(slot.id())).getFirst();
            assertThat(counters.held()).isEqualTo(CAPACITY);
            assertThat(counters.activeHolds()).isEqualTo(CAPACITY);
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
