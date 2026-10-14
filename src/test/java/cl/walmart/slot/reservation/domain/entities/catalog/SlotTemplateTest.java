package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.service.catalog.CutoffPolicy;
import cl.walmart.slot.reservation.domain.service.catalog.FixedLeadTimeCutoffPolicy;
import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlotTemplateTest {

    private static final ZoneId SANTIAGO = CatalogTime.BUSINESS_ZONE;
    private static final CutoffPolicy TWO_HOURS = new FixedLeadTimeCutoffPolicy(Duration.ofHours(2));
    private static final CatalogZoneId ZONE = new CatalogZoneId(UUID.randomUUID());

    private static SlotTemplate template(DayOfWeek day, String start, String end, boolean active) {
        return new SlotTemplate(new SlotTemplateId(UUID.randomUUID()), ZONE, day,
                new TimeRange(LocalTime.parse(start), LocalTime.parse(end)),
                new Capacity(30), new Money(2990), ServiceType.HOME_DELIVERY, active);
    }

    private static ZoneOffsetTransition transition(boolean gap) {
        var rules = SANTIAGO.getRules();
        var cursor = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 8; i++) {
            var next = rules.nextTransition(cursor);
            if (next.isGap() == gap) {
                return next;
            }
            cursor = next.getInstant();
        }
        throw new IllegalStateException("no transition found");
    }

    @Test
    void appliesOnlyToItsDayOfWeek() {
        var monday = template(DayOfWeek.MONDAY, "16:00", "18:00", true);

        assertThat(monday.appliesTo(LocalDate.of(2026, 10, 12))).isTrue();   // lunes
        assertThat(monday.appliesTo(LocalDate.of(2026, 10, 13))).isFalse();  // martes
    }

    @Test
    void instantiateProducesTheWindowWithCurrentPriceCapacityAndCutoff() {
        var tpl = template(DayOfWeek.MONDAY, "16:00", "18:00", true);
        var date = LocalDate.of(2026, 10, 12);

        var slot = tpl.instantiate(date, TWO_HOURS);

        var expectedStart = ZonedDateTime.of(date, LocalTime.of(16, 0), SANTIAGO).toInstant();
        assertThat(slot.templateId()).isEqualTo(tpl.id());
        assertThat(slot.zoneId()).isEqualTo(ZONE);
        assertThat(slot.deliveryDate()).isEqualTo(date);
        assertThat(slot.serviceType()).isEqualTo(ServiceType.HOME_DELIVERY);
        assertThat(slot.startAt()).isEqualTo(expectedStart);
        assertThat(slot.endAt()).isEqualTo(expectedStart.plus(Duration.ofHours(2)));
        assertThat(slot.cutoffAt()).isEqualTo(expectedStart.minus(Duration.ofHours(2)));
        assertThat(slot.price()).isEqualTo(new Money(2990));
        assertThat(slot.capacity()).isEqualTo(new Capacity(30));
    }

    @Test
    void instantiateUsesSantiagoOffsetNotUtc() {
        // 16:00 en Santiago el 12-10-2026 (UTC-3) = 19:00Z
        var slot = template(DayOfWeek.MONDAY, "16:00", "18:00", true)
                .instantiate(LocalDate.of(2026, 10, 12), TWO_HOURS);

        assertThat(slot.startAt()).isEqualTo(Instant.parse("2026-10-12T19:00:00Z"));
    }

    @Test
    void nonExistentStartIsShiftedForwardByTheGapDuration() {
        var gap = transition(true);
        var date = gap.getDateTimeBefore().toLocalDate();
        var gapStart = gap.getDateTimeBefore().toLocalTime();
        var tpl = template(date.getDayOfWeek(), gapStart.toString(), "23:00", true);

        var slot = tpl.instantiate(date, TWO_HOURS);

        // la hora local inexistente se desplaza por la duración del salto: coincide con el instante de la transición
        assertThat(slot.startAt()).isEqualTo(gap.getInstant());
        var local = ZonedDateTime.ofInstant(slot.startAt(), SANTIAGO).toLocalDateTime();
        assertThat(local).isEqualTo(gap.getDateTimeBefore().plus(gap.getDuration()));
    }

    @Test
    void nonExistentEndIsShiftedForwardByTheGapDuration() {
        var gap = transition(true);
        var date = gap.getDateTimeBefore().toLocalDate();
        var gapStart = gap.getDateTimeBefore().toLocalTime();
        var insideGap = gapStart.plusMinutes(30);
        var tpl = template(date.getDayOfWeek(), gapStart.toString(), insideGap.toString(), true);

        var slot = tpl.instantiate(date, TWO_HOURS);

        assertThat(slot.startAt()).isEqualTo(gap.getInstant());
        assertThat(slot.endAt()).isEqualTo(gap.getInstant().plus(Duration.ofMinutes(30)));
        assertThat(slot.startAt()).isBefore(slot.endAt());
    }

    @Test
    void windowThatCollapsesAtTheGapBoundaryIsRejectedByTheInvariant() {
        var gap = transition(true);
        var date = gap.getDateTimeBefore().toLocalDate();
        var gapStart = gap.getDateTimeBefore().toLocalTime();
        // inicio inexistente (se desplaza al fin del salto) y término justo en el fin del salto: startAt == endAt
        var collapsing = template(date.getDayOfWeek(), gapStart.toString(),
                gap.getDateTimeAfter().toLocalTime().toString(), true);

        assertThatThrownBy(() -> collapsing.instantiate(date, TWO_HOURS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startAt");
    }

    @Test
    void ambiguousStartUsesTheFirstOccurrence() {
        var overlap = transition(false);
        var date = overlap.getDateTimeAfter().toLocalDate();
        var ambiguousTime = overlap.getDateTimeAfter().toLocalTime();
        var tpl = template(date.getDayOfWeek(), ambiguousTime.toString(), "23:59", true);

        var slot = tpl.instantiate(date, TWO_HOURS);

        // primera ocurrencia = con el desfase previo a la transición (el más temprano en la línea de tiempo)
        var firstOccurrence = overlap.getDateTimeAfter().toInstant(overlap.getOffsetBefore());
        var secondOccurrence = overlap.getDateTimeAfter().toInstant(overlap.getOffsetAfter());
        assertThat(slot.startAt()).isEqualTo(firstOccurrence);
        assertThat(slot.startAt()).isBefore(secondOccurrence);
    }

    @Test
    void ambiguousEndUsesTheFirstOccurrence() {
        var overlap = transition(false);
        var date = overlap.getDateTimeAfter().toLocalDate();
        var ambiguousTime = overlap.getDateTimeAfter().toLocalTime();
        var tpl = template(date.getDayOfWeek(), "20:00", ambiguousTime.plusMinutes(10).toString(), true);

        var slot = tpl.instantiate(date, TWO_HOURS);

        var firstOccurrence = overlap.getDateTimeAfter().plusMinutes(10).toInstant(overlap.getOffsetBefore());
        assertThat(slot.endAt()).isEqualTo(firstOccurrence);
    }

    @Test
    void cutoffEqualsStartMinusTwoHours() {
        var slot = template(DayOfWeek.MONDAY, "09:00", "11:00", true)
                .instantiate(LocalDate.of(2026, 10, 12), TWO_HOURS);

        assertThat(Duration.between(slot.cutoffAt(), slot.startAt())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void cutoffIsNeverAfterStartEvenWithAMisbehavingPolicy() {
        CutoffPolicy afterStart = (template, startAt) -> startAt.plusSeconds(3600);

        var slot = template(DayOfWeek.MONDAY, "09:00", "11:00", true)
                .instantiate(LocalDate.of(2026, 10, 12), afterStart);

        assertThat(slot.cutoffAt()).isEqualTo(slot.startAt());
    }

    @Test
    void overlappingTemplatesInstantiateIndependently() {
        var first = template(DayOfWeek.MONDAY, "16:00", "18:00", true);
        var second = template(DayOfWeek.MONDAY, "17:00", "19:00", true);
        var date = LocalDate.of(2026, 10, 12);

        var a = first.instantiate(date, TWO_HOURS);
        var b = second.instantiate(date, TWO_HOURS);

        assertThat(first.timeRange().overlaps(second.timeRange())).isTrue();
        assertThat(a.templateId()).isNotEqualTo(b.templateId());
        assertThat(a.startAt()).isEqualTo(Instant.parse("2026-10-12T19:00:00Z"));
        assertThat(b.startAt()).isEqualTo(Instant.parse("2026-10-12T20:00:00Z"));
        assertThat(b.endAt()).isEqualTo(Instant.parse("2026-10-12T22:00:00Z"));
    }

    @Test
    void instantiateRejectsADateOfAnotherDayOfWeek() {
        var monday = template(DayOfWeek.MONDAY, "16:00", "18:00", true);

        assertThatThrownBy(() -> monday.instantiate(LocalDate.of(2026, 10, 13), TWO_HOURS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesActiveStatus() {
        assertThat(template(DayOfWeek.MONDAY, "16:00", "18:00", true).active()).isTrue();
        assertThat(template(DayOfWeek.MONDAY, "16:00", "18:00", false).active()).isFalse();
    }
}
