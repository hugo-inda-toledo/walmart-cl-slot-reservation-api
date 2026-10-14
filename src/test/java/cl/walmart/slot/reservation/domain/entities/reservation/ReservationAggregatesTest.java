package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationAggregatesTest {

    private static final Instant CREATED = Instant.parse("2030-01-09T10:00:00Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(600);

    private static SlotSnapshot slot() {
        return new SlotSnapshot(new SlotId(UUID.randomUUID()), UUID.randomUUID(), LocalDate.of(2030, 1, 10),
                Instant.parse("2030-01-10T12:00:00Z"), Instant.parse("2030-01-10T14:00:00Z"),
                Instant.parse("2030-01-10T10:00:00Z"), 3990);
    }

    private static SlotHold hold(HoldStatus status, Instant expiresAt, Instant closedAt) {
        return new SlotHold(HoldId.random(), new CustomerId("c1"), new OrderId("o1"), slot(), status, CREATED,
                expiresAt, closedAt);
    }

    @Test
    void activeHoldIsExpiredOnceTheDatabaseClockReachesItsExpiry() {
        var hold = hold(HoldStatus.ACTIVE, EXPIRES, null);

        assertThat(hold.effectiveStatus(EXPIRES.minusMillis(1))).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.effectiveStatus(EXPIRES)).isEqualTo(HoldStatus.EXPIRED);
        assertThat(hold.effectiveStatus(EXPIRES.plusSeconds(1))).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    void closedHoldsReportTheirPersistedStatus() {
        var closedAt = CREATED.plusSeconds(60);

        for (var status : new HoldStatus[] {HoldStatus.CONFIRMED, HoldStatus.RELEASED, HoldStatus.EXPIRED}) {
            assertThat(hold(status, EXPIRES, closedAt).effectiveStatus(EXPIRES.plusSeconds(5))).isEqualTo(status);
        }
    }

    @Test
    void holdRejectsIncoherentStateAndInstants() {
        assertThatThrownBy(() -> hold(HoldStatus.ACTIVE, EXPIRES, CREATED.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hold(HoldStatus.RELEASED, EXPIRES, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hold(HoldStatus.ACTIVE, CREATED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hold(HoldStatus.ACTIVE, CREATED.minusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Reservation reservation(ReservationStatus status, Instant cancelledAt) {
        return new Reservation(ReservationId.random(), HoldId.random(), new CustomerId("c1"), new OrderId("o1"),
                slot(), status, CREATED, cancelledAt);
    }

    @Test
    void reservationCancellationInstantMatchesItsStatus() {
        assertThat(reservation(ReservationStatus.CONFIRMED, null).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation(ReservationStatus.CANCELLED, CREATED.plusSeconds(5)).cancelledAt()).isNotNull();

        assertThatThrownBy(() -> reservation(ReservationStatus.CONFIRMED, CREATED.plusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservation(ReservationStatus.CANCELLED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
