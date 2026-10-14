package architecturefixtures.infrastructure;

import java.time.Instant;

public final class DirectSystemClockAccess {

    public Instant currentInstant() {
        return Instant.now();
    }
}
