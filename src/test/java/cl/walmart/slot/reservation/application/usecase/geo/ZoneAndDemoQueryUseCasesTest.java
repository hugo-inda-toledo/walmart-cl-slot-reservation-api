package cl.walmart.slot.reservation.application.usecase.geo;

import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.comuna;
import static cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.zone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverFixtures.FakeZones;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import cl.walmart.slot.reservation.domain.repository.geo.DemoAddressRepository;
import cl.walmart.slot.reservation.domain.exception.geo.GeoErrorCode;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneAndDemoQueryUseCasesTest {

    private final FakeZones zones = new FakeZones();
    private final DeliveryZone active = zone("ACTIVA", ZoneStatus.ACTIVE, comuna("13101", "Santiago", true));
    private final DeliveryZone inactive = zone("INACTIVA", ZoneStatus.INACTIVE, comuna("13119", "Maipú", true));

    ZoneAndDemoQueryUseCasesTest() {
        zones.all.addAll(List.of(active, inactive));
    }

    @Test
    void listWithoutFilterReturnsAllAndPassesFilterThrough() {
        var useCase = new ListDeliveryZonesUseCaseImpl(zones);

        assertThat(useCase.list(Optional.empty())).containsExactly(active, inactive);
        assertThat(useCase.list(Optional.of(ZoneStatus.INACTIVE))).containsExactly(inactive);
        assertThat(zones.lastStatusFilter).contains(ZoneStatus.INACTIVE);
    }

    @Test
    void getWithGeometryReturnsIt() {
        zones.geometry = Optional.of(new ZoneGeometry("{\"type\":\"MultiPolygon\",\"coordinates\":[]}"));

        var detail = new GetDeliveryZoneUseCaseImpl(zones).get(active.id(), true);

        assertThat(detail.zone()).isEqualTo(active);
        assertThat(detail.geometry()).isPresent();
    }

    @Test
    void getWithoutIncludeGeometryOmitsIt() {
        zones.geometry = Optional.of(new ZoneGeometry("{}"));

        assertThat(new GetDeliveryZoneUseCaseImpl(zones).get(active.id(), false).geometry()).isEmpty();
    }

    @Test
    void absentGeometryIsNotAnError() {
        var detail = new GetDeliveryZoneUseCaseImpl(zones).get(active.id(), true);

        assertThat(detail.geometry()).isEmpty();
    }

    @Test
    void unknownZoneRaisesZoneNotFound() {
        var useCase = new GetDeliveryZoneUseCaseImpl(zones);
        var id = new DeliveryZoneId(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.get(id, true)).isInstanceOfSatisfying(GeoException.class, e -> {
            assertThat(e.code()).isEqualTo(GeoErrorCode.ZONE_NOT_FOUND);
            assertThat(e.details()).containsEntry("zoneId", id.value().toString());
        });
    }

    @Test
    void listDemoAddressesReturnsRepositoryOrder() {
        var demo = List.<DemoAddress>of();
        DemoAddressRepository repository = () -> demo;

        assertThat(new ListDemoAddressesUseCaseImpl(repository).list()).isSameAs(demo);
    }
}
