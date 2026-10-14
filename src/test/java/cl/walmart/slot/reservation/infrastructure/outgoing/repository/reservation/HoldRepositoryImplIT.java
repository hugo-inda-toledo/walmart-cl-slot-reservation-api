package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class HoldRepositoryImplIT {

    private static final Duration TTL = Duration.ofMinutes(15);

    @Autowired
    private HoldRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ReservationTestSlots slots;
    private TestSlot slot;
    private CustomerId customer;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        slot = slots.createSlot(50);
        customer = new CustomerId(slots.newCustomerId());
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    void insertActiveComputesCreationAndExpiryWithTheDatabaseClock() {
        Instant before = slots.dbNow();

        SlotHold hold = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();

        assertThat(hold.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.closedAt()).isNull();
        assertThat(Duration.between(hold.createdAt(), hold.expiresAt())).isEqualTo(TTL);
        assertThat(hold.createdAt()).isBetween(before.minusSeconds(1), slots.dbNow());
        assertThat(hold.slot()).isEqualTo(snapshot());
        assertThat(hold.customerId()).isEqualTo(customer);
    }

    @Test
    void insertActiveDoesNothingWhenTheOrderAlreadyHasAnActiveHold() {
        OrderId order = order();
        SlotHold first = repository.insertActive(HoldId.random(), customer, order, snapshot(), TTL).orElseThrow();

        assertThat(repository.insertActive(HoldId.random(), customer, order, snapshot(), TTL)).isEmpty();

        assertThat(repository.findActiveForOrder(customer, order)).map(r -> r.hold().id()).contains(first.id());
    }

    @Test
    void insertActiveAllowsANewHoldOnceThePreviousOneIsClosed() {
        OrderId order = order();
        SlotHold first = repository.insertActive(HoldId.random(), customer, order, snapshot(), TTL).orElseThrow();
        repository.markReleased(first.id(), customer).orElseThrow();

        assertThat(repository.insertActive(HoldId.random(), customer, order, snapshot(), TTL)).isPresent();
    }

    @Test
    void findForCustomerReturnsTheHoldWithTheDatabaseNowAndNoReservation() {
        SlotHold hold = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();
        Instant before = slots.dbNow();

        HoldRecord record = repository.findForCustomer(hold.id(), customer).orElseThrow();

        assertThat(record.hold()).isEqualTo(hold);
        assertThat(record.reservationId()).isEmpty();
        assertThat(record.dbNow()).isBetween(before.minusSeconds(1), slots.dbNow());
    }

    @Test
    void findForCustomerExposesTheReservationThatConfirmedTheHold() {
        SlotHold hold = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();
        repository.markConfirmed(hold.id(), customer).orElseThrow();
        UUID reservationId = UUID.randomUUID();
        jdbc.update("insert into reservation_booking (id, hold_id, customer_id, order_id, slot_id, zone_id, "
                + "delivery_date, start_at, end_at, cutoff_at, price_clp, status, confirmed_at) "
                + "select ?, id, customer_id, order_id, slot_id, zone_id, delivery_date, start_at, end_at, "
                + "cutoff_at, price_clp, 'CONFIRMED', now() from reservation_slot_hold where id = ?",
                reservationId, hold.id().value());

        HoldRecord record = repository.findForCustomer(hold.id(), customer).orElseThrow();

        assertThat(record.hold().status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(record.reservationId()).hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(reservationId));
    }

    @Test
    void aHoldOfAnotherCustomerIsInvisibleAndImmutable() {
        OrderId order = order();
        SlotHold hold = repository.insertActive(HoldId.random(), customer, order, snapshot(), TTL).orElseThrow();
        CustomerId other = new CustomerId(slots.newCustomerId());

        assertThat(repository.findForCustomer(hold.id(), other)).isEmpty();
        assertThat(repository.findActiveForOrder(other, order)).isEmpty();
        assertThat(repository.markConfirmed(hold.id(), other)).isEmpty();
        assertThat(repository.markReleased(hold.id(), other)).isEmpty();
        assertThat(repository.expireStaleForOrder(other, order)).isEmpty();
        assertThat(repository.findForCustomer(hold.id(), customer).orElseThrow().hold().status())
                .isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    void markConfirmedTransitionsAnActiveHoldAndThenFailsItsGuard() {
        SlotHold hold = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();

        SlotHold confirmed = repository.markConfirmed(hold.id(), customer).orElseThrow();

        assertThat(confirmed.status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(confirmed.closedAt()).isNotNull();
        assertThat(repository.markConfirmed(hold.id(), customer)).isEmpty();
        assertThat(repository.markReleased(hold.id(), customer)).isEmpty();
    }

    @Test
    void markConfirmedFailsWhenTheHoldIsPastItsExpiryEvenIfStillActive() {
        SlotHold hold = expiredActiveHold(order(), 5);

        assertThat(repository.markConfirmed(hold.id(), customer)).isEmpty();
        assertThat(repository.findForCustomer(hold.id(), customer).orElseThrow().hold().status())
                .isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    void markReleasedTransitionsOnlyActiveHolds() {
        SlotHold hold = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();

        SlotHold released = repository.markReleased(hold.id(), customer).orElseThrow();

        assertThat(released.status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(released.closedAt()).isNotNull();
        assertThat(repository.markReleased(hold.id(), customer)).isEmpty();
        assertThat(repository.markConfirmed(hold.id(), customer)).isEmpty();
    }

    @Test
    void expireStaleForOrderExpiresOnlyAnOverdueActiveHold() {
        OrderId freshOrder = order();
        repository.insertActive(HoldId.random(), customer, freshOrder, snapshot(), TTL).orElseThrow();
        assertThat(repository.expireStaleForOrder(customer, freshOrder)).isEmpty();
        assertThat(repository.expireStaleForOrder(customer, order())).isEmpty();

        OrderId staleOrder = order();
        SlotHold stale = expiredActiveHold(staleOrder, 5);

        SlotHold expired = repository.expireStaleForOrder(customer, staleOrder).orElseThrow();

        assertThat(expired.id()).isEqualTo(stale.id());
        assertThat(expired.status()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(expired.closedAt()).isNotNull();
        assertThat(repository.findActiveForOrder(customer, staleOrder)).isEmpty();
        assertThat(repository.insertActive(HoldId.random(), customer, staleOrder, snapshot(), TTL)).isPresent();
    }

    @Test
    void expireDueBatchExpiresOverdueHoldsOldestFirstUpToTheLimit() {
        List<SlotHold> overdue = List.of(expiredActiveHold(order(), 40_000), expiredActiveHold(order(), 30_000),
                expiredActiveHold(order(), 20_000));
        SlotHold fresh = repository.insertActive(HoldId.random(), customer, order(), snapshot(), TTL).orElseThrow();

        List<SlotHold> batch = repository.expireDueBatch(2);

        assertThat(batch).extracting(SlotHold::id)
                .containsExactlyInAnyOrder(overdue.get(0).id(), overdue.get(1).id());
        assertThat(batch).allSatisfy(h -> {
            assertThat(h.status()).isEqualTo(HoldStatus.EXPIRED);
            assertThat(h.closedAt()).isNotNull();
        });
        assertThat(repository.findForCustomer(overdue.get(2).id(), customer).orElseThrow().hold().status())
                .isEqualTo(HoldStatus.ACTIVE);
        assertThat(repository.findForCustomer(fresh.id(), customer).orElseThrow().hold().status())
                .isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    void concurrentBatchesNeverShareRows() throws Exception {
        Set<HoldId> mine = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            mine.add(expiredActiveHold(order(), 50_000 + i).id());
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch firstHoldsLocks = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<SlotHold>> first = pool.submit(() -> tx.execute(status -> {
                List<SlotHold> rows = repository.expireDueBatch(3);
                firstHoldsLocks.countDown();
                await(secondDone);
                return rows;
            }));
            assertThat(firstHoldsLocks.await(30, TimeUnit.SECONDS)).isTrue();
            Future<List<SlotHold>> second = pool.submit(() -> tx.execute(status -> repository.expireDueBatch(3)));
            List<SlotHold> secondRows = second.get(30, TimeUnit.SECONDS);
            secondDone.countDown();
            List<SlotHold> firstRows = first.get(30, TimeUnit.SECONDS);

            Set<HoldId> firstIds = new HashSet<>(firstRows.stream().map(SlotHold::id).toList());
            Set<HoldId> secondIds = new HashSet<>(secondRows.stream().map(SlotHold::id).toList());
            assertThat(firstIds).hasSize(3).doesNotContainAnyElementsOf(secondIds);
            assertThat(secondIds).hasSize(3);
            Set<HoldId> union = new HashSet<>(firstIds);
            union.addAll(secondIds);
            assertThat(union).isEqualTo(mine);
        } finally {
            secondDone.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private OrderId order() {
        return new OrderId(slots.newOrderId());
    }

    private SlotSnapshot snapshot() {
        return new SlotSnapshot(new SlotId(slot.id()), slot.zoneId(), slot.deliveryDate(), slot.startAt(),
                slot.endAt(), slot.cutoffAt(), slot.priceClp());
    }

    /** Retención ACTIVE ya vencida según la base (la guarda de vencimiento se evalúa en la base, no aquí). */
    private SlotHold expiredActiveHold(OrderId order, int secondsOverdue) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                + "start_at, end_at, cutoff_at, price_clp, status, created_at, expires_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now() - interval '1 day', "
                + "now() - make_interval(secs => ?))",
                id, customer.value(), order.value(), slot.id(), slot.zoneId(), slot.deliveryDate(),
                OffsetDateTime.ofInstant(slot.startAt(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.endAt(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(slot.cutoffAt(), java.time.ZoneOffset.UTC),
                slot.priceClp(), (double) secondsOverdue);
        return new SlotHold(new HoldId(id), customer, order, snapshot(), HoldStatus.ACTIVE,
                Instant.now().minusSeconds(86_400), Instant.now().minusSeconds(secondsOverdue), null);
    }
}
