package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogValueObjectsTest {

    @Test
    void identifiersRejectNullAndKeepValue() {
        var uuid = UUID.randomUUID();

        assertThat(new CatalogZoneId(uuid).value()).isEqualTo(uuid);
        assertThat(new SlotTemplateId(uuid).value()).isEqualTo(uuid);
        assertThat(new DeliverySlotId(uuid).value()).isEqualTo(uuid);
        assertThatNullPointerException().isThrownBy(() -> new CatalogZoneId(null));
        assertThatNullPointerException().isThrownBy(() -> new SlotTemplateId(null));
        assertThatNullPointerException().isThrownBy(() -> new DeliverySlotId(null));
    }

    @Test
    void moneyAcceptsZeroAndPositiveButNotNegative() {
        assertThat(new Money(0).amountClp()).isZero();
        assertThat(new Money(3_990).amountClp()).isEqualTo(3_990);
        assertThatThrownBy(() -> new Money(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capacityAcceptsZeroButNotNegative() {
        assertThat(new Capacity(0).value()).isZero();
        assertThat(new Capacity(40).value()).isEqualTo(40);
        assertThatThrownBy(() -> new Capacity(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeRangeRequiresStartBeforeEnd() {
        var range = new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(range.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(range.end()).isEqualTo(LocalTime.of(11, 0));
        assertThatThrownBy(() -> new TimeRange(LocalTime.of(11, 0), LocalTime.of(11, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(LocalTime.of(12, 0), LocalTime.of(11, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException().isThrownBy(() -> new TimeRange(null, LocalTime.of(11, 0)));
        assertThatNullPointerException().isThrownBy(() -> new TimeRange(LocalTime.of(9, 0), null));
    }

    @Test
    void timeRangeOverlapIsHalfOpen() {
        var base = new TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0));

        assertThat(base.overlaps(new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 0)))).isTrue();
        assertThat(base.overlaps(new TimeRange(LocalTime.of(8, 0), LocalTime.of(9, 30)))).isTrue();
        assertThat(base.overlaps(new TimeRange(LocalTime.of(9, 30), LocalTime.of(10, 0)))).isTrue();
        assertThat(base.overlaps(base)).isTrue();
        // contiguos no se solapan
        assertThat(base.overlaps(new TimeRange(LocalTime.of(11, 0), LocalTime.of(13, 0)))).isFalse();
        assertThat(base.overlaps(new TimeRange(LocalTime.of(7, 0), LocalTime.of(9, 0)))).isFalse();
        assertThat(base.overlaps(new TimeRange(LocalTime.of(14, 0), LocalTime.of(16, 0)))).isFalse();
    }

    @Test
    void serviceTypeExposesHomeDelivery() {
        assertThat(ServiceType.valueOf("HOME_DELIVERY")).isEqualTo(ServiceType.HOME_DELIVERY);
    }
}
