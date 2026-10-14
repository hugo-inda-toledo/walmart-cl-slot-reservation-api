package cl.walmart.slot.reservation.domain.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PricingPolicyTest {

    private static DeliverySlot slot(long price) {
        var start = Instant.parse("2026-10-14T13:00:00Z");
        return new DeliverySlot(new DeliverySlotId(UUID.randomUUID()), new CatalogZoneId(UUID.randomUUID()),
                LocalDate.of(2026, 10, 14), start, start.plusSeconds(7200), start.minusSeconds(7200),
                new Money(price), 10, 0, 0);
    }

    @Test
    void basePolicyReturnsMaterializedPrice() {
        PricingPolicy base = new TemplatePricingPolicyImpl();
        assertThat(base.priceFor(slot(2990))).isEqualTo(new Money(2990));
        assertThat(base.priceFor(slot(0))).isEqualTo(new Money(0));
    }

    @Test
    void surchargeDecoratorExtendsWithoutModifyingBase() {
        PricingPolicy base = new TemplatePricingPolicyImpl();
        PricingPolicy surcharge = s -> new Money(base.priceFor(s).amountClp() + 500);
        assertThat(surcharge.priceFor(slot(2990))).isEqualTo(new Money(3490));
        assertThat(base.priceFor(slot(2990))).isEqualTo(new Money(2990));
    }
}
