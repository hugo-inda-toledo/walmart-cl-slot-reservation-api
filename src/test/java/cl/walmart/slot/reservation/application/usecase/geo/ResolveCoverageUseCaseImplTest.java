package cl.walmart.slot.reservation.application.usecase.geo;

import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.address;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.comuna;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.exception.geo.AddressNotCoveredException;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolveCoverageUseCaseImplTest {

    private final Comuna comuna = comuna("13101", "Santiago", true);
    private final Address address = address(comuna, null);

    @Test
    void returnsCoveredResult() {
        var covered = new CoverageResult.Covered(comuna, zone("CENTRO", ZoneStatus.ACTIVE, comuna),
                ResolutionMethod.COMUNA_CODE, false);
        var useCase = new ResolveCoverageUseCaseImpl(a -> covered);

        assertThat(useCase.resolve(address)).isSameAs(covered);
    }

    @Test
    void notCoveredBecomesAddressNotCoveredWithReasonAndComuna() {
        var useCase = new ResolveCoverageUseCaseImpl(
                a -> new CoverageResult.NotCovered(NotCoveredReason.ZONE_INACTIVE, Optional.of(comuna)));

        assertThatThrownBy(() -> useCase.resolve(address))
                .isInstanceOfSatisfying(AddressNotCoveredException.class, e -> {
                    assertThat(e.code()).isEqualTo(GeoErrorCode.ADDRESS_NOT_COVERED);
                    assertThat(e.details()).containsEntry("reason", "ZONE_INACTIVE")
                            .containsEntry("comunaCode", "13101");
                });
    }

    @Test
    void notCoveredWithoutComunaOmitsComunaCode() {
        var useCase = new ResolveCoverageUseCaseImpl(
                a -> new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty()));

        assertThatThrownBy(() -> useCase.resolve(address))
                .isInstanceOfSatisfying(AddressNotCoveredException.class,
                        e -> assertThat(e.details()).containsOnlyKeys("reason"));
    }
}
