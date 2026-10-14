package cl.walmart.slot.reservation.application.service.geo;

import cl.walmart.slot.reservation.domain.service.geo.CoverageService;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.POINT;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.address;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.comuna;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.application.service.geo.resolver.ComunaBoundaryResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.ComunaCodeResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.CoverageResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.ExclusionAreaResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.InclusionAreaResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.NearestComunaResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeComunas;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeZones;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Recorre la cadena real de resolvers (en el orden del diseño) sobre fakes de repositorio.
 */
class CoverageServiceImplTest {

    private final FakeComunas comunas = new FakeComunas();
    private final FakeZones zones = new FakeZones();
    private final Comuna laSerena = comuna("04101", "La Serena", true);
    private final Comuna coquimbo = comuna("04102", "Coquimbo", true);
    private final Comuna vina = comuna("05109", "Viña del Mar", false);
    private final DeliveryZone activeZone = zone("LA_SERENA_COQUIMBO", ZoneStatus.ACTIVE, laSerena, coquimbo);
    private final DeliveryZone inactiveZone = zone("MAIPU", ZoneStatus.INACTIVE, coquimbo);

    private CoverageService service() {
        comunas.with(laSerena, coquimbo, vina);
        List<CoverageResolver> chain = List.of(new ExclusionAreaResolver(zones), new InclusionAreaResolver(zones),
                new ComunaBoundaryResolver(), new NearestComunaResolver(comunas, new BorderTolerance(300)),
                new ComunaCodeResolver());
        return new CoverageServiceImpl(comunas, zones, chain);
    }

    private void assignActive() {
        zones.assigned.put(laSerena.code(), activeZone);
        zones.assigned.put(coquimbo.code(), activeZone);
    }

    @Test
    void methodComunaBoundary() {
        assignActive();
        comunas.covering = Optional.of(laSerena);

        var result = service().resolve(address(laSerena, POINT));

        assertThat(result).isEqualTo(
                new CoverageResult.Covered(laSerena, activeZone, ResolutionMethod.COMUNA_BOUNDARY, false));
    }

    @Test
    void methodZoneArea() {
        zones.areas.put(ZoneAreaKind.INCLUSION, activeZone);
        comunas.covering = Optional.of(coquimbo);

        var result = service().resolve(address(laSerena, POINT));

        assertThat(result).isEqualTo(
                new CoverageResult.Covered(coquimbo, activeZone, ResolutionMethod.ZONE_AREA, true));
    }

    @Test
    void methodNearestComuna() {
        assignActive();
        comunas.nearest = Optional.of(laSerena);

        var result = service().resolve(address(laSerena, POINT));

        assertThat(result).isEqualTo(
                new CoverageResult.Covered(laSerena, activeZone, ResolutionMethod.NEAREST_COMUNA, false));
    }

    @Test
    void methodComunaCodeWithoutPoint() {
        assignActive();

        var result = service().resolve(address(coquimbo, null));

        assertThat(result).isEqualTo(
                new CoverageResult.Covered(coquimbo, activeZone, ResolutionMethod.COMUNA_CODE, false));
    }

    @Test
    void comunaDiffersFromDeclaredWhenPointFallsInAnotherComuna() {
        assignActive();
        comunas.covering = Optional.of(coquimbo);

        var result = (CoverageResult.Covered) service().resolve(address(laSerena, POINT));

        assertThat(result.comuna()).isEqualTo(coquimbo);
        assertThat(result.comunaDiffersFromDeclared()).isTrue();
    }

    @Test
    void reasonExcludedAreaReportsComunaAtPoint() {
        assignActive();
        zones.areas.put(ZoneAreaKind.EXCLUSION, activeZone);
        comunas.covering = Optional.of(coquimbo);

        var result = service().resolve(address(coquimbo, POINT));

        assertThat(result).isEqualTo(new CoverageResult.NotCovered(NotCoveredReason.EXCLUDED_AREA,
                Optional.of(coquimbo)));
    }

    @Test
    void exclusionTakesPrecedenceOverInclusion() {
        zones.areas.put(ZoneAreaKind.EXCLUSION, activeZone);
        zones.areas.put(ZoneAreaKind.INCLUSION, activeZone);

        var result = service().resolve(address(coquimbo, POINT));

        assertThat(result).isInstanceOf(CoverageResult.NotCovered.class);
        assertThat(((CoverageResult.NotCovered) result).reason()).isEqualTo(NotCoveredReason.EXCLUDED_AREA);
    }

    @Test
    void reasonOutsideServiceAreaWhenPointOutsideAllBoundaries() {
        assignActive();

        var result = service().resolve(address(laSerena, POINT));

        assertThat(result).isEqualTo(new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA,
                Optional.empty()));
    }

    @Test
    void reasonComunaNotServedWhenNoZoneAssigned() {
        var result = service().resolve(address(vina, null));

        assertThat(result).isEqualTo(new CoverageResult.NotCovered(NotCoveredReason.COMUNA_NOT_SERVED,
                Optional.of(vina)));
    }

    @Test
    void reasonZoneInactiveWhenAssignedZoneIsInactive() {
        zones.assigned.put(coquimbo.code(), inactiveZone);
        comunas.covering = Optional.of(coquimbo);

        var result = service().resolve(address(coquimbo, POINT));

        assertThat(result).isEqualTo(new CoverageResult.NotCovered(NotCoveredReason.ZONE_INACTIVE,
                Optional.of(coquimbo)));
    }

    @Test
    void unknownDeclaredComunaFailsBeforeTheChain() {
        var service = service();
        var ghost = comuna("99999", "Fantasma", true);
        comunas.covering = Optional.of(coquimbo);

        assertThatThrownBy(() -> service.resolve(address(ghost, POINT)))
                .isInstanceOfSatisfying(GeoException.class,
                        e -> assertThat(e.code()).isEqualTo(GeoErrorCode.UNKNOWN_COMUNA));
        assertThat(comunas.coveringCalls).isZero();
    }

    @Test
    void streetAndNumberDoNotAffectResolution() {
        assignActive();
        comunas.covering = Optional.of(laSerena);
        var service = service();
        var a = new Address("Monjitas", "879", laSerena.code(),
                Optional.of(POINT));
        var b = new Address("Otra", "1", laSerena.code(), Optional.of(POINT));

        assertThat(service.resolve(a)).isEqualTo(service.resolve(b));
    }
}
