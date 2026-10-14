package cl.walmart.slot.reservation.application.service.geo.resolver;

import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.POINT;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.address;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.comuna;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeComunas;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeZones;
import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CoverageResolversTest {

    private final FakeComunas comunas = new FakeComunas();
    private final FakeZones zones = new FakeZones();
    private final Comuna declared = comuna("04101", "La Serena", true);
    private final Comuna other = comuna("04102", "Coquimbo", true);
    private final DeliveryZone zoneA = zone("ZONA_A", ZoneStatus.ACTIVE, other);

    private ResolutionContext context(GeoPoint point) {
        return new ResolutionContext(address(declared, point), declared, comunas);
    }

    // --- ExclusionAreaResolver

    @Test
    void exclusionExcludesWhenPointInsideActiveExclusionArea() {
        zones.areas.put(ZoneAreaKind.EXCLUSION, zoneA);

        var decision = new ExclusionAreaResolver(zones).decide(context(POINT));

        assertThat(decision).isInstanceOf(ResolverDecision.Excluded.class);
    }

    @Test
    void exclusionPassesWithoutPointOrWithoutArea() {
        zones.areas.put(ZoneAreaKind.EXCLUSION, zoneA);
        var resolver = new ExclusionAreaResolver(zones);

        assertThat(resolver.decide(context(null))).isInstanceOf(ResolverDecision.Pass.class);
        zones.areas.clear();
        assertThat(resolver.decide(context(POINT))).isInstanceOf(ResolverDecision.Pass.class);
    }

    // --- InclusionAreaResolver

    @Test
    void inclusionMatchesZoneUsingComunaAtPoint() {
        zones.areas.put(ZoneAreaKind.INCLUSION, zoneA);
        comunas.covering = Optional.of(other);

        var decision = new InclusionAreaResolver(zones).decide(context(POINT));

        assertThat(decision).isEqualTo(new ResolverDecision.ZoneMatched(zoneA, other));
    }

    @Test
    void inclusionFallsBackToDeclaredComunaWhenNoBoundaryCoversPoint() {
        zones.areas.put(ZoneAreaKind.INCLUSION, zoneA);

        var decision = new InclusionAreaResolver(zones).decide(context(POINT));

        assertThat(decision).isEqualTo(new ResolverDecision.ZoneMatched(zoneA, declared));
    }

    @Test
    void inclusionPassesWithoutPointOrWithoutArea() {
        var resolver = new InclusionAreaResolver(zones);

        assertThat(resolver.decide(context(null))).isInstanceOf(ResolverDecision.Pass.class);
        assertThat(resolver.decide(context(POINT))).isInstanceOf(ResolverDecision.Pass.class);
    }

    // --- ComunaBoundaryResolver

    @Test
    void boundaryMatchesComunaAtPoint() {
        comunas.covering = Optional.of(other);

        var decision = new ComunaBoundaryResolver().decide(context(POINT));

        assertThat(decision).isEqualTo(new ResolverDecision.ComunaMatched(other, ResolutionMethod.COMUNA_BOUNDARY));
    }

    @Test
    void boundaryPassesWhenNoComunaAtPointOrNoPoint() {
        var resolver = new ComunaBoundaryResolver();

        assertThat(resolver.decide(context(POINT))).isInstanceOf(ResolverDecision.Pass.class);
        assertThat(resolver.decide(context(null))).isInstanceOf(ResolverDecision.Pass.class);
    }

    // --- NearestComunaResolver

    @Test
    void nearestMatchesComunaWithinToleranceUsingConfiguredTolerance() {
        comunas.nearest = Optional.of(declared);
        var tolerance = new BorderTolerance(300);

        var decision = new NearestComunaResolver(comunas, tolerance).decide(context(POINT));

        assertThat(decision).isEqualTo(new ResolverDecision.ComunaMatched(declared, ResolutionMethod.NEAREST_COMUNA));
        assertThat(comunas.lastTolerance).isEqualTo(tolerance);
    }

    @Test
    void nearestPassesWhenNothingWithinToleranceOrNoPoint() {
        var resolver = new NearestComunaResolver(comunas, new BorderTolerance(300));

        assertThat(resolver.decide(context(POINT))).isInstanceOf(ResolverDecision.Pass.class);
        assertThat(resolver.decide(context(null))).isInstanceOf(ResolverDecision.Pass.class);
    }

    // --- ComunaCodeResolver

    @Test
    void codeResolverMatchesDeclaredComunaWithoutPoint() {
        var decision = new ComunaCodeResolver().decide(context(null));

        assertThat(decision).isEqualTo(new ResolverDecision.ComunaMatched(declared, ResolutionMethod.COMUNA_CODE));
    }

    @Test
    void codeResolverMatchesDeclaredComunaWithoutBoundaryEvenWithPoint() {
        var noBoundary = comuna("05109", "Viña del Mar", false);
        var ctx = new ResolutionContext(address(noBoundary, POINT), noBoundary, comunas);

        var decision = new ComunaCodeResolver().decide(ctx);

        assertThat(decision).isEqualTo(new ResolverDecision.ComunaMatched(noBoundary, ResolutionMethod.COMUNA_CODE));
    }

    @Test
    void codeResolverPassesWhenPointAndDeclaredHasBoundary() {
        assertThat(new ComunaCodeResolver().decide(context(POINT))).isInstanceOf(ResolverDecision.Pass.class);
    }

    // --- ResolutionContext

    @Test
    void comunaAtPointIsQueriedOnlyOnce() {
        comunas.covering = Optional.of(other);
        var ctx = context(POINT);

        ctx.comunaAtPoint();
        ctx.comunaAtPoint();
        new ComunaBoundaryResolver().decide(ctx);

        assertThat(comunas.coveringCalls).isEqualTo(1);
    }

    @Test
    void comunaAtPointIsEmptyAndNotQueriedWithoutPoint() {
        var ctx = context(null);

        assertThat(ctx.comunaAtPoint()).isEmpty();
        assertThat(comunas.coveringCalls).isZero();
    }

    // --- Orden de la cadena

    @Test
    void resolversDeclareDesignOrder() {
        var ordered = java.util.List.of(ExclusionAreaResolver.class, InclusionAreaResolver.class,
                ComunaBoundaryResolver.class, NearestComunaResolver.class, ComunaCodeResolver.class);

        var orders = ordered.stream()
                .map(c -> c.getAnnotation(org.springframework.core.annotation.Order.class).value()).toList();

        assertThat(orders).containsExactly(100, 200, 300, 400, 500);
    }
}
