package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeoCodesTest {

    @Test
    void regionCodeAcceptsTwoDigits() {
        assertThat(new RegionCode("13").value()).isEqualTo("13");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", "131", "1a", " 1", "١٣"})
    void regionCodeRejectsInvalidFormatAsRegionNotFound(String value) {
        assertThatThrownBy(() -> new RegionCode(value))
                .isInstanceOfSatisfying(GeoException.class, e -> {
                    assertThat(e.code()).isEqualTo(GeoErrorCode.REGION_NOT_FOUND);
                    assertThat(e.details()).doesNotContainValue(null);
                });
    }

    @Test
    void provinceCodeDerivesRegion() {
        var province = new ProvinceCode("131");

        assertThat(province.value()).isEqualTo("131");
        assertThat(province.regionCode()).isEqualTo(new RegionCode("13"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "13", "1311", "13a"})
    void provinceCodeRejectsInvalidFormat(String value) {
        assertThatThrownBy(() -> new ProvinceCode(value))
                .isInstanceOfSatisfying(GeoException.class, e -> {
                    assertThat(e.code()).isEqualTo(GeoErrorCode.REGION_NOT_FOUND);
                    assertThat(e.details()).doesNotContainValue(null);
                });
    }

    @Test
    void comunaCodeDerivesRegionAndProvince() {
        var comuna = new ComunaCode("13101");

        assertThat(comuna.regionCode()).isEqualTo(new RegionCode("13"));
        assertThat(comuna.provinceCode()).isEqualTo(new ProvinceCode("131"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1310", "131011", "1310a", "abcde"})
    void comunaCodeRejectsInvalidFormatAsUnknownComuna(String value) {
        assertThatThrownBy(() -> new ComunaCode(value))
                .isInstanceOfSatisfying(GeoException.class, e -> {
                    assertThat(e.code()).isEqualTo(GeoErrorCode.UNKNOWN_COMUNA);
                    assertThat(e.details()).doesNotContainValue(null);
                });
    }
}
