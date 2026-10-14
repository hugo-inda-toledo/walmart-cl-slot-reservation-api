package architecturefixtures.infrastructure;

import java.time.Clock;
import java.time.Instant;

public final class ClockInjectedTimeAccess {

    public Instant currentInstant(Clock clock) {
        return Instant.now(clock);
    }
}
