package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverySlotTest {

    private static final Instant START = Instant.parse("2026-10-14T13:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-10-14T11:00:00Z");

    private static DeliverySlot slot(int capacity, int reserved, int held) {
        return new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), new CatalogZoneId(UUID.randomUUID()),
                LocalDate.of(2026, 10, 14), START, START.plusSeconds(7200), CUTOFF, new Money(2990),
                capacity, reserved, held);
    }

    @Test
    void remainingIsCapacityMinusReservedAndHeld() {
        assertThat(slot(10, 3, 2).remaining()).isEqualTo(5);
        assertThat(slot(0, 0, 0).remaining()).isZero();
        assertThat(slot(10, 10, 0).remaining()).isZero();
    }

    @Test
    void rejectsCounterInvariantViolations() {
        assertThatThrownBy(() -> slot(10, 8, 3)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> slot(10, -1, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> slot(10, 0, -1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> slot(-1, 0, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startedAndCutoffReachedDependOnInstant() {
        var slot = slot(10, 0, 0);
        assertThat(slot.hasStartedAt(START.minusMillis(1))).isFalse();
        assertThat(slot.hasStartedAt(START)).isTrue();
        assertThat(slot.isCutoffPassedAt(CUTOFF.minusMillis(1))).isFalse();
        assertThat(slot.isCutoffPassedAt(CUTOFF)).isTrue();
    }

    @Test
    void statusIsAvailableBeforeCutoffWithRemaining() {
        assertThat(slot(10, 2, 1).statusAt(CUTOFF.minusSeconds(1))).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void statusIsSoldOutWhenNoRemainingBeforeCutoff() {
        assertThat(slot(10, 7, 3).statusAt(CUTOFF.minusSeconds(1))).isEqualTo(SlotStatus.SOLD_OUT);
        assertThat(slot(0, 0, 0).statusAt(CUTOFF.minusSeconds(1))).isEqualTo(SlotStatus.SOLD_OUT);
    }

    @Test
    void cutoffTakesPrecedenceOverSoldOutAndAvailable() {
        assertThat(slot(10, 10, 0).statusAt(CUTOFF)).isEqualTo(SlotStatus.CUTOFF_PASSED);
        assertThat(slot(10, 0, 0).statusAt(CUTOFF)).isEqualTo(SlotStatus.CUTOFF_PASSED);
    }
}
