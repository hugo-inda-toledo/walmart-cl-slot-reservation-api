package cl.walmart.slot.reservation.domain.exception.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CatalogErrorsTest {

    @Test
    void errorCodesCarryTheirCategoryAndStableValue() {
        assertThat(CatalogErrorCode.ADDRESS_NOT_COVERED.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
        assertThat(CatalogErrorCode.ADDRESS_NOT_COVERED.value()).isEqualTo("ADDRESS_NOT_COVERED");
        assertThat(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE.value()).isEqualTo("DELIVERY_DATE_OUT_OF_RANGE");
    }

    @Test
    void addressNotCoveredCarriesReasonAndComunaWhenPresent() {
        var e = CatalogException.addressNotCovered("OUTSIDE_COVERAGE", "13101");

        assertThat(e.code()).isEqualTo(CatalogErrorCode.ADDRESS_NOT_COVERED);
        assertThat(e.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
        assertThat(e.details()).containsEntry("reason", "OUTSIDE_COVERAGE").containsEntry("comunaCode", "13101");
        assertThat(e.getMessage()).isEqualTo("La dirección no tiene cobertura de despacho.");
    }

    @Test
    void addressNotCoveredOmitsAbsentDetailsWithoutNulls() {
        var e = CatalogException.addressNotCovered("COMUNA_NOT_SERVED", null);
        var none = CatalogException.addressNotCovered(null, null);

        assertThat(e.details()).containsOnlyKeys("reason");
        assertThat(none.details()).isEmpty();
        assertThat(e.details().values()).doesNotContainNull();
    }

    @Test
    void dateOutOfRangeCarriesIsoDates() {
        var e = CatalogException.deliveryDateOutOfRange(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17));

        assertThat(e.code()).isEqualTo(CatalogErrorCode.DELIVERY_DATE_OUT_OF_RANGE);
        assertThat(e.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(e.details())
                .containsEntry("date", "2026-11-01")
                .containsEntry("minDate", "2026-10-11")
                .containsEntry("maxDate", "2026-10-17");
        assertThat(e.details().values()).doesNotContainNull();
        assertThat(e.getMessage()).isEqualTo("La fecha solicitada está fuera del período consultable.");
    }
}
