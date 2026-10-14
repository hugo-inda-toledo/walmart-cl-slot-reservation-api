package cl.walmart.slot.reservation.domain.entities.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DeliveryDateLabelTest {

    // 2026-10-11 es domingo
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    @Test
    void todayIsLabelledHoy() {
        assertThat(DeliveryDateLabel.of(TODAY, TODAY)).isEqualTo("Hoy");
    }

    @Test
    void otherDatesUseFixedSpanishAbbreviations() {
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 12), TODAY)).isEqualTo("Lun");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 13), TODAY)).isEqualTo("Mar");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 14), TODAY)).isEqualTo("Mié");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 15), TODAY)).isEqualTo("Jue");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 16), TODAY)).isEqualTo("Vie");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 17), TODAY)).isEqualTo("Sáb");
        assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 18), TODAY)).isEqualTo("Dom");
    }

    @Test
    void doesNotDependOnJvmLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            assertThat(DeliveryDateLabel.of(LocalDate.of(2026, 10, 14), TODAY)).isEqualTo("Mié");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
