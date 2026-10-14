package cl.walmart.slot.reservation.infrastructure.outgoing.metrics.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent.Kind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerReservationMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerReservationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerReservationMetrics(registry);
    }

    private double count(String name, String tag, String value) {
        var counter = registry.find(name).tag(tag, value).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void holdLifecycleKindsIncrementHoldCountersWithEventTag() {
        metrics.lifecycle(Kind.HOLD_CREATED, 1);
        metrics.lifecycle(Kind.HOLD_CONFIRMED, 1);
        metrics.lifecycle(Kind.HOLD_RELEASED, 1);
        metrics.lifecycle(Kind.HOLD_EXPIRED, 1);

        assertThat(count("reservation.holds", "event", "created")).isEqualTo(1.0);
        assertThat(count("reservation.holds", "event", "confirmed")).isEqualTo(1.0);
        assertThat(count("reservation.holds", "event", "released")).isEqualTo(1.0);
        assertThat(count("reservation.holds", "event", "expired")).isEqualTo(1.0);
    }

    @Test
    void cancelledReservationIncrementsReservationsCounter() {
        metrics.lifecycle(Kind.RESERVATION_CANCELLED, 1);

        assertThat(count("reservation.reservations", "event", "cancelled")).isEqualTo(1.0);
        assertThat(registry.find("reservation.holds").counters()).isEmpty();
    }

    @Test
    void lifecycleIncrementsByCount() {
        metrics.lifecycle(Kind.HOLD_EXPIRED, 5);
        metrics.lifecycle(Kind.HOLD_EXPIRED, 2);
        metrics.lifecycle(Kind.RESERVATION_CANCELLED, 3);

        assertThat(count("reservation.holds", "event", "expired")).isEqualTo(7.0);
        assertThat(count("reservation.reservations", "event", "cancelled")).isEqualTo(3.0);
    }

    @Test
    void zeroCountDoesNotChangeCounter() {
        metrics.lifecycle(Kind.HOLD_CREATED, 0);

        assertThat(count("reservation.holds", "event", "created")).isZero();
    }

    @Test
    void holdRejectedUsesCodeTag() {
        metrics.holdRejected("SLOT_FULL");
        metrics.holdRejected("SLOT_FULL");
        metrics.holdRejected("SLOT_NOT_FOUND");

        assertThat(count("reservation.holds.rejected", "code", "SLOT_FULL")).isEqualTo(2.0);
        assertThat(count("reservation.holds.rejected", "code", "SLOT_NOT_FOUND")).isEqualTo(1.0);
    }

    @Test
    void idempotencyResultsUseResultTag() {
        metrics.idempotency("replayed");
        metrics.idempotency("key_reused");
        metrics.idempotency("in_progress");
        metrics.idempotency("replayed");

        assertThat(count("reservation.idempotency", "result", "replayed")).isEqualTo(2.0);
        assertThat(count("reservation.idempotency", "result", "key_reused")).isEqualTo(1.0);
        assertThat(count("reservation.idempotency", "result", "in_progress")).isEqualTo(1.0);
    }
}
