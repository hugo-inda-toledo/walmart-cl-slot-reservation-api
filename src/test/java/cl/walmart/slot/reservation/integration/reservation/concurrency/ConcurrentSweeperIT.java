package cl.walmart.slot.reservation.integration.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseExpiredHoldsUseCase;
import cl.walmart.slot.reservation.support.reservation.ReservationInvariants;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** SKIP LOCKED reparte vencimientos entre sweepers sin devolver un cupo dos veces. */
@IntegrationTest
class ConcurrentSweeperIT {

    private static final int HOLDS = 300;
    private static final int SWEEPERS = 4;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private JdbcTemplate jdbc;
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
    void expiresThreeHundredHoldsExactlyOnceAcrossFourConcurrentSweepers() throws Exception {
        List<TestSlot> testSlots = List.of(slots.createSlot(120), slots.slot().dayOffset(1).capacity(120).create(),
                slots.slot().dayOffset(2).capacity(120).create());
        for (int index = 0; index < HOLDS; index++) {
            insertExpiredActiveHold(testSlots.get(index % testSlots.size()));
        }

        CountDownLatch ready = new CountDownLatch(SWEEPERS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(SWEEPERS);
        try {
            List<Future<Integer>> released = new ArrayList<>();
            for (int index = 0; index < SWEEPERS; index++) {
                released.add(executor.submit(() -> {
                    ready.countDown();
                    await(start, "los cuatro sweepers");
                    int total = 0;
                    int next;
                    do {
                        next = releaseExpiredHolds.releaseExpired();
                        total += next;
                    } while (next > 0);
                    return total;
                }));
            }
            await(ready, "los cuatro sweepers preparados");
            start.countDown();
            int totalReleased = 0;
            for (Future<Integer> result : released) {
                totalReleased += result.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }

            assertThat(totalReleased).isEqualTo(HOLDS);
            String[] slotIds = testSlots.stream().map(TestSlot::id).map(UUID::toString).toArray(String[]::new);
            assertThat(jdbc.queryForObject("select count(*) from reservation_slot_hold where status = 'EXPIRED' "
                    + "and slot_id = any (?::uuid[])", Integer.class, (Object) slotIds)).isEqualTo(HOLDS);
            assertThat(invariants.countersOf(testSlots.stream().map(TestSlot::id).toList()))
                    .allSatisfy(counters -> assertThat(counters.held()).isZero());
            invariants.assertConserved(testSlots.stream().map(TestSlot::id).toList());
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }

    private void insertExpiredActiveHold(TestSlot slot) {
        String customer = slots.newCustomerId();
        jdbc.update("update catalog_delivery_slot set held = held + 1 where id = ?", slot.id());
        jdbc.update("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                        + "start_at, end_at, cutoff_at, price_clp, status, created_at, expires_at) values "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now() - interval '1 day', now() - interval '1 second')",
                UUID.randomUUID(), customer, slots.newOrderId(), slot.id(), slot.zoneId(), slot.deliveryDate(),
                OffsetDateTime.ofInstant(slot.startAt(), ZoneOffset.UTC), OffsetDateTime.ofInstant(slot.endAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.cutoffAt(), ZoneOffset.UTC), slot.priceClp());
    }

    private static void await(CountDownLatch latch, String awaited) throws InterruptedException {
        assertThat(latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("espera de %s", awaited).isTrue();
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    }
}
