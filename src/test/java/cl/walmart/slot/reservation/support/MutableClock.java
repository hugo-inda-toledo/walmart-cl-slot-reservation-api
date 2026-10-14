package cl.walmart.slot.reservation.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    @Override
    public synchronized ZoneId getZone() {
        return zone;
    }

    @Override
    public synchronized Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public synchronized Instant instant() {
        return instant;
    }

    public synchronized void setInstant(Instant instant) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
    }

    public synchronized void advance(Duration duration) {
        instant = instant.plus(Objects.requireNonNull(duration, "duration must not be null"));
    }
}
