package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.Capacity;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplate;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplateId;
import cl.walmart.slot.reservation.domain.entities.catalog.TimeRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FixedLeadTimeCutoffPolicyTest {

    private static final SlotTemplate TEMPLATE = new SlotTemplate(new SlotTemplateId(UUID.randomUUID()),
            new CatalogZoneId(UUID.randomUUID()), DayOfWeek.MONDAY,
            new TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0)),
            new Capacity(10), new Money(1000), ServiceType.HOME_DELIVERY, true);
    private static final Instant START = Instant.parse("2026-10-12T19:00:00Z");

    @Test
    void cutoffIsStartMinusLeadTime() {
        var policy = new FixedLeadTimeCutoffPolicy(Duration.ofHours(2));

        assertThat(policy.cutoffFor(TEMPLATE, START)).isEqualTo(Instant.parse("2026-10-12T17:00:00Z"));
    }

    @Test
    void zeroLeadTimeClosesAtStart() {
        assertThat(new FixedLeadTimeCutoffPolicy(Duration.ZERO).cutoffFor(TEMPLATE, START)).isEqualTo(START);
    }

    @Test
    void twentyFourHoursIsTheMaximum() {
        var policy = new FixedLeadTimeCutoffPolicy(Duration.ofHours(24));

        assertThat(policy.cutoffFor(TEMPLATE, START)).isEqualTo(Instant.parse("2026-10-11T19:00:00Z"));
    }

    @Test
    void rejectsNegativeAndOverTwentyFourHours() {
        assertThatThrownBy(() -> new FixedLeadTimeCutoffPolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedLeadTimeCutoffPolicy(Duration.ofHours(24).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedLeadTimeCutoffPolicy(null))
                .isInstanceOf(NullPointerException.class);
    }
}
