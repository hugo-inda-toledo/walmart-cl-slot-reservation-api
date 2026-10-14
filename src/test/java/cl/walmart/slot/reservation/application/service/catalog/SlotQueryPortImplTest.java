package cl.walmart.slot.reservation.application.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SlotQueryPortImplTest {
    private final CatalogZoneId zone = new CatalogZoneId(UUID.randomUUID());
    private final LocalDate date = LocalDate.of(2031, 2, 3);
    private final Instant now = Instant.parse("2031-02-03T12:00:00Z");
    @Test
    void returnsNoServiceForHolidayAndOnlyAvailableSlotsForRange() {
        var available = slot(0, 0); var sold = slot(1, 5);
        SlotQueryRepository repository = new SlotQueryRepository() {
            public List<DeliverySlot> findByZoneAndDates(CatalogZoneId z, LocalDate f, LocalDate t) { return List.of(sold, available); }
            public Optional<DeliverySlot> findById(DeliverySlotId id) { return Optional.of(available); }
        };
        HolidayRepository holidays = (f, t) -> new HolidayCalendar(Set.of(date), Map.of());
        var port = new SlotQueryPortImpl(repository, holidays, DeliverySlot::basePrice, new CatalogSettings(7, 14),
                new MutableClock(now, ZoneOffset.UTC));
        assertThat(port.findSlot(available.id().value())).hasValueSatisfying(view ->
                assertThat(view.status()).isEqualTo(SlotView.Status.NO_SERVICE));
        assertThat(port.findAvailableSlots(zone.value(), date, date)).isEmpty();
        assertThatThrownBy(() -> port.findAvailableSlots(zone.value(), date.plusDays(2), date)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excludesStartedAndSoldOutSlotsAndKeepsAvailableSlotsOrderedWithDecoratedPrice() {
        var early = slot(0, 0); var late = slot(1, 0);
        var started = new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), zone, date,
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)), now.minus(Duration.ofHours(2)),
                new Money(1000), 5, 0, 0);
        SlotQueryRepository repository = new SlotQueryRepository() {
            public List<DeliverySlot> findByZoneAndDates(CatalogZoneId z, LocalDate f, LocalDate t) { return List.of(late, started, early); }
            public Optional<DeliverySlot> findById(DeliverySlotId id) { return Optional.empty(); }
        };
        var port = new SlotQueryPortImpl(repository, (f, t) -> new HolidayCalendar(Set.of(), Map.of()),
                slot -> new Money(slot.basePrice().amountClp() + 1), new CatalogSettings(7, 14),
                new MutableClock(now, ZoneOffset.UTC));

        assertThat(port.findAvailableSlots(zone.value(), date, date)).extracting(SlotView::slotId)
                .containsExactly(early.id().value(), late.id().value());
        assertThat(port.findAvailableSlots(zone.value(), date, date)).allSatisfy(view -> assertThat(view.priceClp()).isEqualTo(2991));
        assertThatThrownBy(() -> port.findAvailableSlots(zone.value(), date, date.plusDays(14))).isInstanceOf(IllegalArgumentException.class);
    }
    private DeliverySlot slot(int hour, int reserved) {
        var start = date.atTime(18 + hour, 0).atZone(CatalogTime.BUSINESS_ZONE).toInstant();
        return new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), zone, date, start, start.plus(Duration.ofHours(1)),
                start.minus(Duration.ofHours(2)), new Money(2990), 5, reserved, 0);
    }
}
