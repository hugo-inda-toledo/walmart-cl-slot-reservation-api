package cl.walmart.slot.reservation.infrastructure.outgoing.metrics.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Contadores Micrometer de reservas (requisitos 11.1 y 11.2). */
@Component
public class MicrometerReservationMetrics implements ReservationMetrics {

    private static final String HOLDS = "reservation.holds";
    private static final String RESERVATIONS = "reservation.reservations";
    private static final String HOLDS_REJECTED = "reservation.holds.rejected";
    private static final String IDEMPOTENCY = "reservation.idempotency";

    private final MeterRegistry registry;

    public MicrometerReservationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void lifecycle(ReservationLifecycleEvent.Kind kind, int count) {
        if (count <= 0) {
            return;
        }
        switch (kind) {
            case HOLD_CREATED -> increment(HOLDS, "event", "created", count);
            case HOLD_CONFIRMED -> increment(HOLDS, "event", "confirmed", count);
            case HOLD_RELEASED -> increment(HOLDS, "event", "released", count);
            case HOLD_EXPIRED -> increment(HOLDS, "event", "expired", count);
            case RESERVATION_CANCELLED -> increment(RESERVATIONS, "event", "cancelled", count);
        }
    }

    @Override
    public void holdRejected(String code) {
        increment(HOLDS_REJECTED, "code", code, 1);
    }

    @Override
    public void idempotency(String result) {
        increment(IDEMPOTENCY, "result", result, 1);
    }

    private void increment(String name, String tag, String value, int amount) {
        registry.counter(name, tag, value).increment(amount);
    }
}
