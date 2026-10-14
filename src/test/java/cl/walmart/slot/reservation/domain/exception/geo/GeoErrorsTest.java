package cl.walmart.slot.reservation.domain.exception.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeoErrorsTest {

    @Test
    void errorCodesCarryTheirCategoryAndStableValue() {
        assertThat(GeoErrorCode.REGION_NOT_FOUND.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(GeoErrorCode.ZONE_NOT_FOUND.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(GeoErrorCode.UNKNOWN_COMUNA.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(GeoErrorCode.INCOMPLETE_COORDINATES.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(GeoErrorCode.INVALID_COORDINATES.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(GeoErrorCode.ADDRESS_NOT_COVERED.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
        assertThat(GeoErrorCode.ADDRESS_NOT_COVERED.value()).isEqualTo("ADDRESS_NOT_COVERED");
    }

    @Test
    void regionNotFoundCarriesRegionCode() {
        var e = GeoException.regionNotFound("99");

        assertThat(e.code()).isEqualTo(GeoErrorCode.REGION_NOT_FOUND);
        assertThat(e.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(e.details()).containsEntry("regionCode", "99");
        assertThat(e.getMessage()).contains("región");
    }

    @Test
    void zoneNotFoundCarriesZoneId() {
        var id = UUID.randomUUID();
        var e = GeoException.zoneNotFound(new DeliveryZoneId(id));

        assertThat(e.code()).isEqualTo(GeoErrorCode.ZONE_NOT_FOUND);
        assertThat(e.details()).containsEntry("zoneId", id.toString());
        assertThat(e.getMessage()).contains("zona");
    }

    @Test
    void unknownComunaOmitsNullCode() {
        var withCode = GeoException.unknownComuna("99999");
        var withoutCode = GeoException.unknownComuna(null);

        assertThat(withCode.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
        assertThat(withCode.details()).containsEntry("comunaCode", "99999");
        assertThat(withoutCode.details()).isEmpty();
        assertThat(withCode.getMessage()).contains("comuna");
    }

    @Test
    void coordinateErrorsHaveNoDetails() {
        var incomplete = GeoException.incompleteCoordinates();
        var invalid = GeoException.invalidCoordinates();

        assertThat(incomplete.code()).isEqualTo(GeoErrorCode.INCOMPLETE_COORDINATES);
        assertThat(incomplete.details()).isEmpty();
        assertThat(incomplete.getMessage()).contains("latitud").contains("longitud");
        assertThat(invalid.code()).isEqualTo(GeoErrorCode.INVALID_COORDINATES);
        assertThat(invalid.details()).isEmpty();
        assertThat(invalid.category()).isEqualTo(ErrorCategory.INVALID_INPUT);
    }

    @Test
    void addressNotCoveredIncludesComunaOnlyWhenPresent() {
        var with = new AddressNotCoveredException(NotCoveredReason.ZONE_INACTIVE,
                Optional.of(new ComunaCode("13114")));
        var without = new AddressNotCoveredException(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty());

        assertThat(with.code()).isEqualTo(GeoErrorCode.ADDRESS_NOT_COVERED);
        assertThat(with.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
        assertThat(with.details())
                .containsEntry("reason", "ZONE_INACTIVE")
                .containsEntry("comunaCode", "13114");
        assertThat(without.details()).containsOnlyKeys("reason");
        assertThat(without.getMessage()).isEqualTo("La dirección no tiene cobertura de despacho.");
    }
}
