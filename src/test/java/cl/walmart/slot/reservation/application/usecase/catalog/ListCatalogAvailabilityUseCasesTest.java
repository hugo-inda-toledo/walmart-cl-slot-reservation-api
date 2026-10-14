package cl.walmart.slot.reservation.application.usecase.catalog;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ListCatalogAvailabilityUseCasesTest {
    private final CatalogZoneId zoneId = new CatalogZoneId(UUID.randomUUID());
    private final ResolvedZone zone = new ResolvedZone(zoneId, "LSC", "La Serena-Coquimbo", "04101", "La Serena");
    private final Instant now = Instant.parse("2031-02-03T12:00:00Z");

    @Test
    void listsConsecutiveDatesWithLabelsAndCurrentStatusFromOneInstant() {
        var clock = new MutableClock(now, ZoneOffset.UTC);
        var useCase = new ListAvailableDatesUseCaseImpl(query -> zone, days(List.of(day(LocalDate.of(2031, 2, 3), 0),
                day(LocalDate.of(2031, 2, 4), 5))), new CatalogSettings(2, 14), clock);

        var result = useCase.list(new AddressQuery("04101", null, null));

        assertThat(result.dates()).extracting(DeliveryDateView::label).containsExactly("Hoy", "Mar");
        assertThat(result.dates()).extracting(DeliveryDateView::status)
                .containsExactly(DeliveryDateStatus.AVAILABLE, DeliveryDateStatus.SOLD_OUT);
    }

    @Test
    void validatesDateBeforeResolvingTheAddressAndUsesDecoratedPrice() {
        var clock = new MutableClock(now, ZoneOffset.UTC);
        var called = new boolean[1];
        AddressZoneService addresses = query -> { called[0] = true; return zone; };
        PricingPolicy decorated = slot -> new Money(slot.basePrice().amountClp() + 500);
        var useCase = new ListSlotsForDateUseCaseImpl(addresses, days(List.of(day(LocalDate.of(2031, 2, 3), 0))),
                new CatalogSettings(1, 14), decorated, clock);

        assertThatThrownBy(() -> useCase.list(new AddressQuery("bad", null, null), LocalDate.of(2031, 2, 4)))
                .isInstanceOfSatisfying(cl.walmart.slot.reservation.domain.exception.DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE));
        assertThat(called[0]).isFalse();
        var result = useCase.list(new AddressQuery("04101", null, null), LocalDate.of(2031, 2, 3));
        assertThat(result.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.price()).isEqualTo(new Money(3490));
            assertThat(slot.status()).isEqualTo(SlotStatus.AVAILABLE);
        });
    }

    @Test
    void capturesClockExactlyOncePerRequestSoPeriodAndStatusShareNow() {
        var clock = new CountingClock(now);
        var useCase = new ListAvailableDatesUseCaseImpl(query -> zone,
                days(List.of(day(LocalDate.of(2031, 2, 3), 0))), new CatalogSettings(1, 14), clock);

        useCase.list(new AddressQuery("04101", null, null));

        assertThat(clock.calls).isOne();
    }

    @Test
    void recalculatesCutoffFromCachedDayWhenTheClockAdvances() {
        var clock = new MutableClock(Instant.parse("2031-02-03T18:00:00Z"), ZoneOffset.UTC);
        var date = LocalDate.of(2031, 2, 3);
        var start = Instant.parse("2031-02-03T21:00:00Z");
        var cachedSlot = new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), zoneId, date, start,
                start.plus(Duration.ofHours(2)), Instant.parse("2031-02-03T19:00:00Z"), new Money(2990), 5, 0, 0);
        var service = days(List.of(new ZoneDayAvailability(zoneId, date, false, List.of(cachedSlot))));
        var useCase = new ListSlotsForDateUseCaseImpl(query -> zone, service, new CatalogSettings(1, 14),
                DeliverySlot::basePrice, clock);

        assertThat(useCase.list(new AddressQuery("04101", null, null), date).slots()).singleElement()
                .extracting(PricedSlotView::status).isEqualTo(SlotStatus.AVAILABLE);
        clock.advance(Duration.ofHours(1));
        assertThat(useCase.list(new AddressQuery("04101", null, null), date).slots()).singleElement()
                .extracting(PricedSlotView::status).isEqualTo(SlotStatus.CUTOFF_PASSED);
    }

    private AvailabilityService days(List<ZoneDayAvailability> values) {
        return (ignored, dates) -> values.stream().filter(day -> dates.contains(day.date())).toList();
    }
    private ZoneDayAvailability day(LocalDate date, int reserved) {
        var start = date.atTime(18, 0).atZone(CatalogTime.BUSINESS_ZONE).toInstant();
        var slot = new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), zoneId, date, start, start.plus(Duration.ofHours(2)),
                start.minus(Duration.ofHours(2)), new Money(2990), 5, reserved, 0);
        return new ZoneDayAvailability(zoneId, date, false, List.of(slot));
    }
    private static final class CountingClock extends Clock {
        private final Instant instant; int calls;
        CountingClock(Instant instant) { this.instant = instant; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { calls++; return instant; }
    }
}
