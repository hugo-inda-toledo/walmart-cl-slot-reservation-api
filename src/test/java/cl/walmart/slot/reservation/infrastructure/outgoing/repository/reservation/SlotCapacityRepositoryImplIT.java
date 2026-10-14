package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.CounterRelease;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.SlotCapacityRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SlotCapacityRepositoryImplIT {

    @Autowired
    private SlotCapacityRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private ReservationTestSlots slots;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    void tryHoldAcquiresTheSlotAndReturnsItsZoneDay() {
        TestSlot slot = slots.createSlot(2);

        HoldCapacityOutcome outcome = repository.tryHold(new SlotId(slot.id()));

        assertThat(outcome).isEqualTo(new HoldCapacityOutcome.Acquired(new ZoneDay(slot.zoneId(), slot.deliveryDate())));
        assertThat(counters(slot)).containsExactly(0, 1);
    }

    @Test
    void tryHoldReportsSoldOutWithTheDatabaseClockWhenCapacityIsExhausted() {
        TestSlot slot = slots.createSlot(1);
        repository.tryHold(new SlotId(slot.id()));
        Instant before = slots.dbNow();

        HoldCapacityOutcome outcome = repository.tryHold(new SlotId(slot.id()));

        assertThat(outcome).isInstanceOf(HoldCapacityOutcome.SoldOut.class);
        Instant dbNow = ((HoldCapacityOutcome.SoldOut) outcome).dbNow();
        assertThat(dbNow).isBetween(before.minusSeconds(1), slots.dbNow());
        assertThat(counters(slot)).containsExactly(0, 1);
    }

    @Test
    void tryHoldReportsCutoffPassedEvenWhenCapacityRemains() {
        TestSlot slot = slots.slot().capacity(3).cutoffFromDbNow(Duration.ofMinutes(-5)).create();

        HoldCapacityOutcome outcome = repository.tryHold(new SlotId(slot.id()));

        assertThat(outcome).isEqualTo(new HoldCapacityOutcome.CutoffPassed(slot.cutoffAt()));
        assertThat(counters(slot)).containsExactly(0, 0);
    }

    @Test
    void tryHoldReportsCutoffPassedBeforeSoldOutWhenBothApply() {
        TestSlot slot = slots.slot().capacity(0).cutoffFromDbNow(Duration.ofMinutes(-5)).create();

        assertThat(repository.tryHold(new SlotId(slot.id()))).isInstanceOf(HoldCapacityOutcome.CutoffPassed.class);
    }

    @Test
    void tryHoldReportsNotFoundForAnUnknownSlot() {
        assertThat(repository.tryHold(new SlotId(UUID.randomUUID()))).isEqualTo(new HoldCapacityOutcome.NotFound());
    }

    @Test
    void movesHeldToReservedAndReportsTheZoneDay() {
        TestSlot slot = slots.createSlot(2);
        repository.tryHold(new SlotId(slot.id()));

        var moved = repository.moveHeldToReserved(new SlotId(slot.id()));

        assertThat(moved).contains(new ZoneDay(slot.zoneId(), slot.deliveryDate()));
        assertThat(counters(slot)).containsExactly(1, 0);
    }

    @Test
    void moveHeldToReservedAffectsNothingWhenThereIsNoHeldCapacity() {
        TestSlot slot = slots.createSlot(2);

        assertThat(repository.moveHeldToReserved(new SlotId(slot.id()))).isEmpty();
        assertThat(counters(slot)).containsExactly(0, 0);
    }

    @Test
    void releasesHeldCapacityOfSeveralSlotsInOneStatement() {
        TestSlot first = slots.createSlot(5);
        TestSlot second = slots.slot().capacity(5).dayOffset(1).create();
        TestSlot untouched = slots.slot().capacity(5).dayOffset(2).create();
        for (int i = 0; i < 3; i++) {
            repository.tryHold(new SlotId(first.id()));
        }
        repository.tryHold(new SlotId(second.id()));
        repository.tryHold(new SlotId(untouched.id()));

        CounterRelease release = repository.releaseHeld(
                Map.of(new SlotId(first.id()), 2, new SlotId(second.id()), 1));

        assertThat(release.rowsTouched()).isEqualTo(2);
        assertThat(release.zoneDays()).containsExactlyInAnyOrder(
                new ZoneDay(first.zoneId(), first.deliveryDate()),
                new ZoneDay(second.zoneId(), second.deliveryDate()));
        assertThat(counters(first)).containsExactly(0, 1);
        assertThat(counters(second)).containsExactly(0, 0);
        assertThat(counters(untouched)).containsExactly(0, 1);
    }

    @Test
    void releaseHeldIgnoresUnknownSlotsAndEmptyInput() {
        TestSlot slot = slots.createSlot(2);
        repository.tryHold(new SlotId(slot.id()));

        CounterRelease release = repository.releaseHeld(
                Map.of(new SlotId(slot.id()), 1, new SlotId(UUID.randomUUID()), 1));

        assertThat(release.rowsTouched()).isEqualTo(1);
        assertThat(repository.releaseHeld(Map.of()).rowsTouched()).isZero();
    }

    @Test
    void releasesReservedCapacityOnlyWhenThereIsSome() {
        TestSlot slot = slots.createSlot(2);
        SlotId id = new SlotId(slot.id());
        assertThat(repository.releaseReserved(id)).isEmpty();

        repository.tryHold(id);
        repository.moveHeldToReserved(id);

        assertThat(repository.releaseReserved(id)).contains(new ZoneDay(slot.zoneId(), slot.deliveryDate()));
        assertThat(counters(slot)).containsExactly(0, 0);
    }

    @Test
    void capacityConstraintOfTheCatalogIsTheLastLineOfDefense() {
        TestSlot slot = slots.createSlot(1);
        repository.tryHold(new SlotId(slot.id()));

        assertThatThrownBy(() -> jdbc.update(
                "update catalog_delivery_slot set held = held + 1 where id = ?", slot.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("catalog_delivery_slot_capacity_check");
        assertThat(counters(slot)).containsExactly(0, 1);
    }

    @Test
    void everyCounterWriteIncrementsTheSlotVersion() {
        TestSlot slot = slots.createSlot(3);
        SlotId id = new SlotId(slot.id());
        long version = version(slot);

        repository.tryHold(id);
        assertThat(version(slot)).isEqualTo(++version);
        repository.tryHold(id);
        assertThat(version(slot)).isEqualTo(++version);
        repository.moveHeldToReserved(id);
        assertThat(version(slot)).isEqualTo(++version);
        repository.releaseHeld(Map.of(id, 1));
        assertThat(version(slot)).isEqualTo(++version);
        repository.releaseReserved(id);
        assertThat(version(slot)).isEqualTo(++version);
    }

    private int[] counters(TestSlot slot) {
        return jdbc.queryForObject("select reserved, held from catalog_delivery_slot where id = ?",
                (rs, row) -> new int[] {rs.getInt("reserved"), rs.getInt("held")}, slot.id());
    }

    private long version(TestSlot slot) {
        return jdbc.queryForObject("select version from catalog_delivery_slot where id = ?", Long.class, slot.id());
    }
}
