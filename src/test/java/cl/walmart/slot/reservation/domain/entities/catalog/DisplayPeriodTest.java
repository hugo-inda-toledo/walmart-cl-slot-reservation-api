package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.exception.catalog.CatalogErrorCode;
import cl.walmart.slot.reservation.domain.exception.catalog.CatalogException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DisplayPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private static DisplayPeriod period(int days) {
        return new DisplayPeriod(TODAY, days);
    }

    @Test
    void listsTodayThroughTodayPlusNMinusOne() {
        var period = period(3);
        assertThat(period.dates()).containsExactly(TODAY, TODAY.plusDays(1), TODAY.plusDays(2));
        assertThat(period.lastDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void rejectsDaysOutsideOneToFourteen() {
        assertThatThrownBy(() -> period(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> period(15)).isInstanceOf(IllegalArgumentException.class);
        assertThat(period(1).dates()).hasSize(1);
        assertThat(period(14).dates()).hasSize(14);
    }

    @Test
    void fromClockUsesSantiagoDateNotClockZone() {
        // 2026-10-12T02:00Z es 2026-10-11 23:00 en Santiago (UTC-3)
        var clock = new MutableClock(Instant.parse("2026-10-12T02:00:00Z"), ZoneOffset.UTC);
        assertThat(DisplayPeriod.from(clock, 7).today()).isEqualTo(TODAY);
    }

    @Test
    void requireContainsAcceptsBoundaries() {
        var period = period(7);
        assertThatCode(() -> period.requireContains(TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> period.requireContains(TODAY.plusDays(6))).doesNotThrowAnyException();
    }

    @Test
    void requireContainsRejectsOutOfRangeWithDetails() {
        var period = period(7);
        for (var date : new LocalDate[] {TODAY.minusDays(1), TODAY.plusDays(7)}) {
            assertThatThrownBy(() -> period.requireContains(date))
                    .isInstanceOfSatisfying(CatalogException.class, ex -> {
                        assertThat(ex.code()).isEqualTo(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE);
                        assertThat(ex.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
                        assertThat(ex.details()).containsEntry("date", date.toString())
                                .containsEntry("minDate", "2026-10-11")
                                .containsEntry("maxDate", "2026-10-17");
                    });
        }
    }
}
