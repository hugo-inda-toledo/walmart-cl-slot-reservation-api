package cl.walmart.slot.reservation.application.service.geo;

import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.comuna;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.geo.ZoneResolution;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionQuery;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeZones;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ZoneResolutionPortImplTest {

    private final Comuna santiago = comuna("13101", "Santiago", true);
    private final DeliveryZone active = zone("CENTRO", ZoneStatus.ACTIVE, santiago);
    private final FakeZones zones = new FakeZones();

    private ZoneResolutionPortImpl port(Function<Address, CoverageResult> service) {
        return new ZoneResolutionPortImpl(service::apply, zones);
    }

    @Test
    void everyDomainMethodMapsToPublishedMethodByName() {
        for (var method : ResolutionMethod.values()) {
            var port = port(a -> new CoverageResult.Covered(santiago, active, method, true));

            var result = (ZoneResolution.Covered) port.resolveZone(new ZoneResolutionQuery("13101", null, null));

            assertThat(result.method().name()).isEqualTo(method.name());
            assertThat(result.zoneId()).isEqualTo(active.id().value());
            assertThat(result.zoneCode()).isEqualTo("CENTRO");
            assertThat(result.comunaCode()).isEqualTo("13101");
            assertThat(result.comunaName()).isEqualTo("Santiago");
            assertThat(result.comunaDiffersFromDeclared()).isTrue();
        }
        assertThat(ZoneResolution.Method.values()).hasSameSizeAs(ResolutionMethod.values());
    }

    @Test
    void everyDomainReasonMapsToPublishedReasonByName() {
        for (var reason : NotCoveredReason.values()) {
            var port = port(a -> new CoverageResult.NotCovered(reason, Optional.of(santiago)));

            var result = port.resolveZone(new ZoneResolutionQuery("13101", null, null));

            assertThat(result).isEqualTo(new ZoneResolution.NotCovered(
                    ZoneResolution.Reason.valueOf(reason.name()), Optional.of("13101")));
        }
        assertThat(ZoneResolution.Reason.values()).hasSameSizeAs(NotCoveredReason.values());
    }

    @Test
    void notCoveredWithoutComunaIsAValueNotAnException() {
        var port = port(a -> new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty()));

        var result = port.resolveZone(new ZoneResolutionQuery("13101", -33.4, -70.6));

        assertThat(result).isEqualTo(
                new ZoneResolution.NotCovered(ZoneResolution.Reason.OUTSIDE_SERVICE_AREA, Optional.empty()));
    }

    @Test
    void forwardsComunaAndCoordinatesToCoverageService() {
        var captured = new Address[1];
        var port = port(a -> {
            captured[0] = a;
            return new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty());
        });

        port.resolveZone(new ZoneResolutionQuery("13101", -33.4, -70.6));

        assertThat(captured[0].comunaCode().value()).isEqualTo("13101");
        assertThat(captured[0].location()).isPresent();
    }

    @Test
    void invalidInputErrorsPropagate() {
        var port = port(a -> {
            throw GeoException.unknownComuna(a.comunaCode().value());
        });

        assertCode(port, new ZoneResolutionQuery("13101", null, null), GeoErrorCode.UNKNOWN_COMUNA);
        assertCode(port, new ZoneResolutionQuery("13101", -33.4, null), GeoErrorCode.INCOMPLETE_COORDINATES);
        assertCode(port, new ZoneResolutionQuery("13101", 123.0, -70.6), GeoErrorCode.INVALID_COORDINATES);
        assertCode(port, new ZoneResolutionQuery("abc", null, null), GeoErrorCode.UNKNOWN_COMUNA);
    }

    @Test
    void nullOrBlankComunaCodeIsUnknownComuna() {
        var port = port(a -> {
            throw new AssertionError("must not reach the coverage service");
        });

        assertCode(port, new ZoneResolutionQuery(null, null, null), GeoErrorCode.UNKNOWN_COMUNA);
        assertCode(port, new ZoneResolutionQuery("  ", null, null), GeoErrorCode.UNKNOWN_COMUNA);
        assertThatThrownBy(() -> port.resolveZone(new ZoneResolutionQuery(null, null, null)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.details()).isEmpty());
    }

    @Test
    void findZoneReturnsReferenceForActiveAndInactiveAndEmptyWhenMissing() {
        var inactive = zone("MAIPU", ZoneStatus.INACTIVE, comuna("13119", "Maipú", true));
        zones.all.add(active);
        zones.all.add(inactive);
        var port = port(a -> null);

        var activeRef = port.findZone(active.id().value());
        var inactiveRef = port.findZone(inactive.id().value());

        assertThat(activeRef).hasValueSatisfying(r -> {
            assertThat(r.code()).isEqualTo("CENTRO");
            assertThat(r.active()).isTrue();
        });
        assertThat(inactiveRef).hasValueSatisfying(r -> assertThat(r.active()).isFalse());
        assertThat(port.findZone(UUID.randomUUID())).isEmpty();
    }

    @Test
    void doesNotRollBackCallerTransactionOnDomainErrors() {
        var tx = ZoneResolutionPortImpl.class.getAnnotation(Transactional.class);

        assertThat(tx.readOnly()).isTrue();
        assertThat(tx.noRollbackFor()).contains(DomainException.class);
    }

    private static void assertCode(ZoneResolutionPortImpl port, ZoneResolutionQuery query, GeoErrorCode code) {
        assertThatThrownBy(() -> port.resolveZone(query)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
