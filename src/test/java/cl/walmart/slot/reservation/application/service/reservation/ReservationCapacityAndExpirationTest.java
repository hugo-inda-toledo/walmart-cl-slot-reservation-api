package cl.walmart.slot.reservation.application.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent.Kind;
import cl.walmart.slot.reservation.domain.entities.reservation.CounterRelease;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.repository.reservation.SlotCapacityRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ReservationCapacityAndExpirationTest {

    private static final Instant T0 = Instant.parse("2031-03-01T12:00:00Z");
    private static final CustomerId CUSTOMER = new CustomerId("cust-1");

    private final FakeCapacityRepository capacityRepository = new FakeCapacityRepository();
    private final FakeHoldRepository holdRepository = new FakeHoldRepository();
    private final List<Object> published = new ArrayList<>();
    private final SlotCapacityServiceImpl capacity = new SlotCapacityServiceImpl(capacityRepository);
    private final HoldServiceImpl holds = new HoldServiceImpl(holdRepository, capacity, published::add);

    // --- SlotCapacityService ---

    @Test
    void moveHeldToReservedWithoutAffectedRowIsAnInternalError() {
        capacityRepository.moveResult = Optional.empty();

        assertThatThrownBy(() -> capacity.moveHeldToReserved(slotId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releaseReservedWithoutAffectedRowIsAnInternalError() {
        capacityRepository.releaseReservedResult = Optional.empty();

        assertThatThrownBy(() -> capacity.releaseReserved(slotId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releaseHeldTouchingFewerRowsThanWindowsIsAnInternalError() {
        var a = slotId();
        var b = slotId();
        capacityRepository.releaseHeldResult = new CounterRelease(Set.of(zoneDay()), 1);

        assertThatThrownBy(() -> capacity.releaseHeld(Map.of(a, 1, b, 2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releaseHeldReturnsTheChangedZoneDays() {
        var day = zoneDay();
        capacityRepository.releaseHeldResult = new CounterRelease(Set.of(day), 1);

        assertThat(capacity.releaseHeld(Map.of(slotId(), 3))).containsExactly(day);
    }

    @Test
    void releaseHeldWithNoCountsDoesNotTouchTheRepository() {
        assertThat(capacity.releaseHeld(Map.of())).isEmpty();
        assertThat(capacityRepository.releaseHeldCalls).isZero();
    }

    @Test
    void tryHoldAndMovesDelegateToTheRepository() {
        var day = zoneDay();
        capacityRepository.tryHoldResult = new HoldCapacityOutcome.Acquired(day);
        capacityRepository.moveResult = Optional.of(day);
        capacityRepository.releaseReservedResult = Optional.of(day);

        assertThat(capacity.tryHold(slotId())).isEqualTo(new HoldCapacityOutcome.Acquired(day));
        assertThat(capacity.moveHeldToReserved(slotId())).isEqualTo(day);
        assertThat(capacity.releaseReserved(slotId())).isEqualTo(day);
    }

    // --- HoldService ---

    @Test
    void expireDueGroupsReleasedSlotsByWindowAndPublishesTheEvent() {
        var slotA = snapshot(UUID.randomUUID(), LocalDate.of(2031, 3, 5));
        var slotB = snapshot(UUID.randomUUID(), LocalDate.of(2031, 3, 6));
        holdRepository.dueBatch = List.of(hold(slotA), hold(slotA), hold(slotB));
        capacityRepository.releaseHeldResult =
                new CounterRelease(Set.of(slotA.zoneDay(), slotB.zoneDay()), 2);

        var batch = holds.expireDue(10);

        assertThat(holdRepository.lastLimit).isEqualTo(10);
        assertThat(capacityRepository.lastReleased)
                .containsEntry(slotA.slotId(), 2)
                .containsEntry(slotB.slotId(), 1)
                .hasSize(2);
        assertThat(batch.count()).isEqualTo(3);
        assertThat(batch.zoneDays()).containsExactlyInAnyOrder(slotA.zoneDay(), slotB.zoneDay());
        assertThat(published).containsExactly(
                new ReservationLifecycleEvent(Kind.HOLD_EXPIRED, 3, Set.of(slotA.zoneDay(), slotB.zoneDay())));
    }

    @Test
    void expireDueWithNothingDueDoesNotReleaseNorPublish() {
        holdRepository.dueBatch = List.of();

        var batch = holds.expireDue(5);

        assertThat(batch.count()).isZero();
        assertThat(batch.zoneDays()).isEmpty();
        assertThat(capacityRepository.releaseHeldCalls).isZero();
        assertThat(published).isEmpty();
    }

    @Test
    void expireStaleForOrderReleasesTheSlotAndPublishes() {
        var snapshot = snapshot(UUID.randomUUID(), LocalDate.of(2031, 3, 5));
        holdRepository.staleForOrder = Optional.of(hold(snapshot));
        capacityRepository.releaseHeldResult = new CounterRelease(Set.of(snapshot.zoneDay()), 1);

        var result = holds.expireStaleForOrder(CUSTOMER, new OrderId("order-1"));

        assertThat(result).contains(snapshot.zoneDay());
        assertThat(capacityRepository.lastReleased).containsExactly(Map.entry(snapshot.slotId(), 1));
        assertThat(published).containsExactly(
                new ReservationLifecycleEvent(Kind.HOLD_EXPIRED, 1, Set.of(snapshot.zoneDay())));
    }

    @Test
    void expireStaleForOrderWithoutStaleHoldDoesNothing() {
        holdRepository.staleForOrder = Optional.empty();

        assertThat(holds.expireStaleForOrder(CUSTOMER, new OrderId("order-1"))).isEmpty();
        assertThat(capacityRepository.releaseHeldCalls).isZero();
        assertThat(published).isEmpty();
    }

    // --- Transaccionalidad ---

    @Test
    void servicesAreMandatoryAndBusinessRejectionsDoNotRollBack() {
        for (var type : List.of(SlotCapacityServiceImpl.class, HoldServiceImpl.class)) {
            var tx = type.getAnnotation(Transactional.class);
            assertThat(tx).as(type.getSimpleName()).isNotNull();
            assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
            assertThat(tx.noRollbackFor()).contains(ReservationException.class);
        }
    }

    // --- fixtures ---

    private static SlotId slotId() {
        return new SlotId(UUID.randomUUID());
    }

    private static ZoneDay zoneDay() {
        return new ZoneDay(UUID.randomUUID(), LocalDate.of(2031, 3, 5));
    }

    private static SlotSnapshot snapshot(UUID zoneId, LocalDate date) {
        var start = date.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC);
        return new SlotSnapshot(new SlotId(UUID.randomUUID()), zoneId, date, start, start.plus(Duration.ofHours(2)),
                start.minus(Duration.ofHours(4)), 3_000);
    }

    private static SlotHold hold(SlotSnapshot slot) {
        return new SlotHold(HoldId.random(), CUSTOMER, new OrderId("order-" + UUID.randomUUID()), slot,
                HoldStatus.EXPIRED, T0.minus(Duration.ofMinutes(20)), T0.minus(Duration.ofMinutes(5)), T0);
    }

    private static final class FakeCapacityRepository implements SlotCapacityRepository {
        HoldCapacityOutcome tryHoldResult = new HoldCapacityOutcome.NotFound();
        Optional<ZoneDay> moveResult = Optional.empty();
        Optional<ZoneDay> releaseReservedResult = Optional.empty();
        CounterRelease releaseHeldResult = new CounterRelease(Set.of(), 0);
        Map<SlotId, Integer> lastReleased;
        int releaseHeldCalls;

        @Override
        public HoldCapacityOutcome tryHold(SlotId slot) {
            return tryHoldResult;
        }

        @Override
        public Optional<ZoneDay> moveHeldToReserved(SlotId slot) {
            return moveResult;
        }

        @Override
        public CounterRelease releaseHeld(Map<SlotId, Integer> countsBySlot) {
            releaseHeldCalls++;
            lastReleased = countsBySlot;
            return releaseHeldResult;
        }

        @Override
        public Optional<ZoneDay> releaseReserved(SlotId slot) {
            return releaseReservedResult;
        }
    }

    private static final class FakeHoldRepository implements HoldRepository {
        List<SlotHold> dueBatch = List.of();
        Optional<SlotHold> staleForOrder = Optional.empty();
        int lastLimit;

        @Override
        public Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order, SlotSnapshot slot,
                                               Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HoldRecord> findActiveForOrder(CustomerId customer, OrderId order) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SlotHold> expireStaleForOrder(CustomerId customer, OrderId order) {
            return staleForOrder;
        }

        @Override
        public Optional<SlotHold> markConfirmed(HoldId id, CustomerId customer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SlotHold> markReleased(HoldId id, CustomerId customer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SlotHold> expireDueBatch(int limit) {
            lastLimit = limit;
            return dueBatch;
        }
    }
}
