package cl.walmart.slot.reservation.domain.entities.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReservationValueObjectsTest {

    static Stream<String> validOpaqueIds() {
        return Stream.of("a", "cliente-1", "A.b_C-9", "x".repeat(64));
    }

    static Stream<String> invalidOpaqueIds() {
        return Stream.of("", " ", "a b", "x".repeat(65), "ñandú", "a/b", "a\n");
    }

    @ParameterizedTest
    @MethodSource("validOpaqueIds")
    void customerAndOrderIdsAcceptTheOpaquePattern(String value) {
        assertThat(new CustomerId(value).value()).isEqualTo(value);
        assertThat(new OrderId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("invalidOpaqueIds")
    void customerAndOrderIdsRejectOtherValues(String value) {
        assertThatThrownBy(() -> new CustomerId(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderId(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifiersRejectNull() {
        assertThatThrownBy(() -> new CustomerId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HoldId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReservationId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SlotId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void randomIdentifiersAreDistinct() {
        assertThat(HoldId.random()).isNotEqualTo(HoldId.random());
        assertThat(ReservationId.random()).isNotEqualTo(ReservationId.random());
    }

    @Test
    void idempotencyKeyBoundsAreOneToOneHundredTwentyEight() {
        assertThatCode(() -> new IdempotencyKey("k")).doesNotThrowAnyException();
        assertThatCode(() -> new IdempotencyKey("k".repeat(128))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new IdempotencyKey("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(129))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void slotSnapshotEnforcesItsInvariants() {
        var slot = new SlotId(UUID.randomUUID());
        var zone = UUID.randomUUID();
        var date = LocalDate.of(2030, 1, 10);
        var cutoff = Instant.parse("2030-01-10T10:00:00Z");
        var start = Instant.parse("2030-01-10T12:00:00Z");
        var end = Instant.parse("2030-01-10T14:00:00Z");

        var snapshot = new SlotSnapshot(slot, zone, date, start, end, cutoff, 3990);
        assertThat(snapshot.zoneDay()).isEqualTo(new ZoneDay(zone, date));
        assertThatCode(() -> new SlotSnapshot(slot, zone, date, start, end, start, 0)).doesNotThrowAnyException();

        assertThatThrownBy(() -> new SlotSnapshot(slot, zone, date, end, start, cutoff, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlotSnapshot(slot, zone, date, start, start, cutoff, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlotSnapshot(slot, zone, date, start, end, end, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlotSnapshot(slot, zone, date, start, end, cutoff, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alternativeSlotRejectsNegativeValues() {
        var slot = new SlotId(UUID.randomUUID());
        var start = Instant.parse("2030-01-10T12:00:00Z");
        var date = LocalDate.of(2030, 1, 10);

        assertThatCode(() -> new AlternativeSlot(slot, date, start, start.plusSeconds(3600), 0, 0))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new AlternativeSlot(slot, date, start, start.plusSeconds(3600), -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AlternativeSlot(slot, date, start, start.plusSeconds(3600), 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
