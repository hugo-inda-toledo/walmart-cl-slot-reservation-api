package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress.ExpectedCoverage;
import cl.walmart.slot.reservation.domain.repository.geo.DemoAddressRepository;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Contrato del adaptador de direcciones demo contra PostGIS real (8.1, 8.3).
 */
@IntegrationTest
class DemoAddressRepositoryImplIT {

    @Autowired
    private DemoAddressRepository demoAddresses;

    @Test
    void listsTheNineDemoAddressesInPresentationOrder() {
        List<DemoAddress> all = demoAddresses.findAllOrdered();

        assertThat(all).extracting(DemoAddress::id).containsExactly(
                "la-serena-monjitas", "santiago-monjitas", "providencia-av", "chicureo", "la-serena-borde",
                "coquimbo-sin-coordenadas", "vina-sin-cobertura", "maipu-inactiva", "tongoy-excluida");
    }

    @Test
    void mapsCoordinatesAndCoveredExpectationCompletely() {
        DemoAddress first = demoAddresses.findAllOrdered().getFirst();

        assertThat(first.label()).isEqualTo("Monjitas 2334, La Serena");
        assertThat(first.street()).isEqualTo("Monjitas");
        assertThat(first.number()).isEqualTo("2334");
        assertThat(first.comunaCode()).isEqualTo(new ComunaCode("04101"));
        assertThat(first.comunaName()).isEqualTo("La Serena");
        assertThat(first.point()).contains(new GeoPoint(-29.9045, -71.2489));
        assertThat(first.expected()).isEqualTo(new ExpectedCoverage(true, Optional.of("LA_SERENA_COQUIMBO"),
                Optional.of(ResolutionMethod.COMUNA_BOUNDARY), Optional.empty()));
    }

    @Test
    void mapsAddressWithoutCoordinates() {
        DemoAddress address = byId("coquimbo-sin-coordenadas");

        assertThat(address.point()).isEmpty();
        assertThat(address.expected().method()).contains(ResolutionMethod.COMUNA_CODE);
    }

    @Test
    void mapsNotCoveredExpectationWithReasonOnly() {
        DemoAddress address = byId("maipu-inactiva");

        assertThat(address.expected()).isEqualTo(new ExpectedCoverage(false, Optional.empty(), Optional.empty(),
                Optional.of(NotCoveredReason.ZONE_INACTIVE)));
        assertThat(address.point()).contains(new GeoPoint(-33.51, -70.757));
    }

    @Test
    void mapsEveryAddressWithConsistentPointAndExpectation() {
        assertThat(demoAddresses.findAllOrdered()).hasSize(9).allSatisfy(address -> {
            assertThat(address.label()).isNotBlank();
            assertThat(address.street()).isNotBlank();
            assertThat(address.number()).isNotBlank();
            ExpectedCoverage expected = address.expected();
            if (expected.covered()) {
                assertThat(expected.zoneCode()).isPresent();
                assertThat(expected.method()).isPresent();
            } else {
                assertThat(expected.reason()).isPresent();
            }
            if (address.point().isEmpty() && expected.covered()) {
                assertThat(expected.method()).contains(ResolutionMethod.COMUNA_CODE);
            }
        });
    }

    private DemoAddress byId(String id) {
        return demoAddresses.findAllOrdered().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }
}
