package cl.walmart.slot.reservation.domain.entities.geo;

import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoPointAddressTest {

    @Test
    void geoPointAcceptsRangeLimits() {
        assertThat(new GeoPoint(-90, 180).latitude()).isEqualTo(-90);
        assertThat(new GeoPoint(90, -180).longitude()).isEqualTo(-180);
    }

    @ParameterizedTest
    @CsvSource({"90.0001,0", "-90.0001,0", "0,180.0001", "0,-180.0001", "NaN,0", "0,NaN", "Infinity,0", "0,-Infinity"})
    void geoPointRejectsOutOfRangeOrNonFinite(double lat, double lng) {
        assertThatThrownBy(() -> new GeoPoint(lat, lng))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.INVALID_COORDINATES));
    }

    @Test
    void addressWithoutCoordinatesHasNoLocation() {
        var address = Address.of("  Av. Apoquindo ", " 4500 ", "13114", null, null);

        assertThat(address.street()).isEqualTo("Av. Apoquindo");
        assertThat(address.number()).isEqualTo("4500");
        assertThat(address.comunaCode()).isEqualTo(new ComunaCode("13114"));
        assertThat(address.location()).isEmpty();
    }

    @Test
    void addressWithBothCoordinatesHasLocation() {
        var address = Address.of("Calle", "1", "13114", -33.4, -70.6);

        assertThat(address.location()).contains(new GeoPoint(-33.4, -70.6));
    }

    @Test
    void addressWithOneCoordinateIsIncomplete() {
        assertThatThrownBy(() -> Address.of("Calle", "1", "13114", -33.4, null))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.INCOMPLETE_COORDINATES));
        assertThatThrownBy(() -> Address.of("Calle", "1", "13114", null, -70.6))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.INCOMPLETE_COORDINATES));
    }

    @Test
    void addressWithOutOfRangeCoordinatesIsInvalid() {
        assertThatThrownBy(() -> Address.of("Calle", "1", "13114", 91.0, 0.0))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.INVALID_COORDINATES));
    }

    @Test
    void addressRejectsMalformedComunaCode() {
        assertThatThrownBy(() -> Address.of("Calle", "1", "abc", null, null))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.UNKNOWN_COMUNA));
    }

    @Test
    void canonicalConstructorKeepsLocationOptional() {
        var address = new Address("Calle", "1", new ComunaCode("13114"), Optional.empty());

        assertThat(address.location()).isEmpty();
    }
}
