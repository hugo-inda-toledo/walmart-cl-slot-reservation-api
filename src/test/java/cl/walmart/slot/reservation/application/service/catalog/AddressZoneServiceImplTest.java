package cl.walmart.slot.reservation.application.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.exception.catalog.CatalogErrorCode;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolution;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionPort;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionQuery;
import cl.walmart.slot.reservation.domain.port.geo.ZoneReference;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddressZoneServiceImplTest {

    private final UUID zoneId = UUID.randomUUID();

    @Test
    void mapsACoveredAddressToTheCatalogRepresentation() {
        var service = service(query -> new ZoneResolution.Covered(zoneId, "LSC", "La Serena-Coquimbo", "04101",
                "La Serena", ZoneResolution.Method.COMUNA_CODE, false));

        var resolved = service.resolve(new AddressQuery("04101", -29.9, -71.2));

        assertThat(resolved.zoneId().value()).isEqualTo(zoneId);
        assertThat(resolved.zoneCode()).isEqualTo("LSC");
        assertThat(resolved.comunaCode()).isEqualTo("04101");
    }

    @Test
    void turnsAnUncoveredAddressIntoTheCatalogBusinessErrorWithReasonAndComuna() {
        var service = service(query -> new ZoneResolution.NotCovered(ZoneResolution.Reason.ZONE_INACTIVE,
                Optional.of("13119")));

        assertThatThrownBy(() -> service.resolve(new AddressQuery("13119", null, null)))
                .isInstanceOfSatisfying(cl.walmart.slot.reservation.domain.exception.DomainException.class, error -> {
                    assertThat(error.code()).isEqualTo(CatalogErrorCode.ADDRESS_NOT_COVERED);
                    assertThat(error.details()).containsEntry("reason", "ZONE_INACTIVE")
                            .containsEntry("comunaCode", "13119");
                });
    }

    @Test
    void propagatesGeoInputErrorsWithoutWrappingThem() {
        var expected = GeoException.unknownComuna("99999");
        var service = service(query -> { throw expected; });

        assertThatThrownBy(() -> service.resolve(new AddressQuery("99999", null, null))).isSameAs(expected);
    }

    private AddressZoneServiceImpl service(java.util.function.Function<ZoneResolutionQuery, ZoneResolution> resolver) {
        ZoneResolutionPort port = new ZoneResolutionPort() {
            @Override public ZoneResolution resolveZone(ZoneResolutionQuery query) { return resolver.apply(query); }
            @Override public Optional<ZoneReference> findZone(UUID id) { return Optional.empty(); }
        };
        return new AddressZoneServiceImpl(port);
    }
}
