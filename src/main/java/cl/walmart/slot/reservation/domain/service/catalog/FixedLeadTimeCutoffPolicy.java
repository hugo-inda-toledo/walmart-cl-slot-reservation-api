package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Cierre a una anticipación fija: {@code inicio − anticipación}, con anticipación entre 0 y 24 horas.
 */
public final class FixedLeadTimeCutoffPolicy implements CutoffPolicy {

    private static final Duration MAX_LEAD_TIME = Duration.ofHours(24);

    private final Duration leadTime;

    public FixedLeadTimeCutoffPolicy(Duration leadTime) {
        Objects.requireNonNull(leadTime, "leadTime must not be null");
        if (leadTime.isNegative() || leadTime.compareTo(MAX_LEAD_TIME) > 0) {
            throw new IllegalArgumentException("leadTime must be between 0 and 24 hours: " + leadTime);
        }
        this.leadTime = leadTime;
    }

    @Override
    public Instant cutoffFor(SlotTemplate template, Instant startAt) {
        Objects.requireNonNull(startAt, "startAt must not be null");
        return startAt.minus(leadTime);
    }
}
