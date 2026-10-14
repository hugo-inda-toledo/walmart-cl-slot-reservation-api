package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.DeliveryDateStatus;
import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneDayAvailabilityTest {

    private static final CatalogZoneId ZONE = new CatalogZoneId(UUID.randomUUID());
    private static final LocalDate DATE = LocalDate.of(2026, 10, 14);
    private static final Instant D0 = Instant.parse("2026-10-14T12:00:00Z");
    private static final Instant EARLY = Instant.parse("2026-10-14T05:00:00Z");

    /** Ventana con inicio en {@code D0 + startHours}, cierre 2 horas antes y duración de 2 horas. */
    private static DeliverySlot slot(int startHours, int endHours, int capacity, int reserved) {
        var start = D0.plusSeconds(startHours * 3600L);
        return new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), ZONE, DATE, start,
                D0.plusSeconds(endHours * 3600L), start.minusSeconds(7200), new Money(2990), capacity, reserved, 0);
    }

    private static ZoneDayAvailability day(boolean closed, DeliverySlot... slots) {
        return new ZoneDayAvailability(ZONE, DATE, closed, List.of(slots));
    }

    @Test
    void allSoldOutMeansSoldOutDate() {
        var day = day(false, slot(4, 6, 5, 5), slot(6, 8, 5, 5));
        assertThat(day.dateStatusAt(EARLY)).isEqualTo(DeliveryDateStatus.SOLD_OUT);
    }

    @Test
    void anyAvailableMeansAvailableDate() {
        var day = day(false, slot(4, 6, 5, 5), slot(6, 8, 5, 1));
        assertThat(day.dateStatusAt(EARLY)).isEqualTo(DeliveryDateStatus.AVAILABLE);
    }

    @Test
    void allSlotsPastCutoffMeansNoService() {
        var day = day(false, slot(4, 6, 5, 0), slot(6, 8, 5, 0));
        // Cierres 14:00Z y 16:00Z; a las 16:30Z la primera ya inició (oculta) y la segunda pasó su cierre
        assertThat(day.dateStatusAt(Instant.parse("2026-10-14T16:30:00Z"))).isEqualTo(DeliveryDateStatus.NO_SERVICE);
    }

    @Test
    void closedDateIsNoServiceWithEmptyList() {
        var day = day(true, slot(4, 6, 5, 0));
        assertThat(day.dateStatusAt(EARLY)).isEqualTo(DeliveryDateStatus.NO_SERVICE);
        assertThat(day.visibleSlotsAt(EARLY)).isEmpty();
    }

    @Test
    void emptyDayIsNoService() {
        assertThat(day(false).dateStatusAt(EARLY)).isEqualTo(DeliveryDateStatus.NO_SERVICE);
    }

    @Test
    void startedSlotsAreNotVisible() {
        var first = slot(4, 6, 5, 0);
        var second = slot(6, 8, 5, 0);
        var day = day(false, first, second);
        assertThat(day.visibleSlotsAt(first.startAt())).containsExactly(second);
        assertThat(day.visibleSlotsAt(first.startAt().minusMillis(1))).containsExactly(first, second);
    }

    @Test
    void visibleSlotsAreOrderedByStartThenEnd() {
        var late = slot(6, 8, 5, 0);
        var earlyLong = slot(4, 9, 5, 0);
        var earlyShort = slot(4, 6, 5, 0);
        var day = day(false, late, earlyLong, earlyShort);
        assertThat(day.visibleSlotsAt(EARLY)).containsExactly(earlyShort, earlyLong, late);
    }

    @Test
    void dateStatusIsConsistentWithVisibleSlotStatuses() {
        var sold = slot(4, 6, 5, 5);
        var open = slot(6, 8, 5, 2);
        var day = day(false, sold, open);
        var statuses = day.visibleSlotsAt(EARLY).stream().map(s -> s.statusAt(EARLY)).toList();
        assertThat(statuses).containsExactly(SlotStatus.SOLD_OUT, SlotStatus.AVAILABLE);
        assertThat(day.dateStatusAt(EARLY)).isEqualTo(DeliveryDateStatus.AVAILABLE);
    }

    @Test
    void cutoffSlotsDoNotCountAsOpenForDateStatus() {
        var cutoff = slot(4, 6, 5, 0);   // cierre en D0+2h
        var soldOut = slot(6, 8, 5, 5);  // cierre en D0+4h
        var day = day(false, cutoff, soldOut);
        var now = D0.plusSeconds(3 * 3600L); // pasó el cierre de la primera, no la segunda
        assertThat(day.dateStatusAt(now)).isEqualTo(DeliveryDateStatus.SOLD_OUT);
    }

    @Test
    void rejectsSlotFromAnotherZone() {
        var foreign = new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), new CatalogZoneId(UUID.randomUUID()), DATE,
                D0.plusSeconds(4 * 3600L), D0.plusSeconds(6 * 3600L), D0.plusSeconds(2 * 3600L),
                new Money(2990), 5, 0, 0);

        assertThatThrownBy(() -> day(false, foreign))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zoneId");
    }

    @Test
    void rejectsSlotFromAnotherDate() {
        var foreign = new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), ZONE, DATE.plusDays(1),
                D0.plusSeconds(4 * 3600L), D0.plusSeconds(6 * 3600L), D0.plusSeconds(2 * 3600L),
                new Money(2990), 5, 0, 0);

        assertThatThrownBy(() -> day(false, foreign))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }
}
