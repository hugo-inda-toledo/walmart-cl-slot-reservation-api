package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DemoAddressEntity;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress.ExpectedCoverage;
import cl.walmart.slot.reservation.domain.repository.geo.DemoAddressRepository;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class DemoAddressRepositoryImpl implements DemoAddressRepository {

    private final DemoAddressJpaRepository jpaRepository;

    DemoAddressRepositoryImpl(DemoAddressJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<DemoAddress> findAllOrdered() {
        return jpaRepository.findAllByOrderBySortOrderAsc().stream().map(DemoAddressRepositoryImpl::toDomain).toList();
    }

    private static DemoAddress toDomain(DemoAddressEntity e) {
        Optional<GeoPoint> point = e.getLatitude() == null || e.getLongitude() == null
                ? Optional.empty()
                : Optional.of(new GeoPoint(e.getLatitude().doubleValue(), e.getLongitude().doubleValue()));
        var expected = new ExpectedCoverage(
                e.isExpectedCovered(),
                Optional.ofNullable(e.getExpectedZoneCode()),
                Optional.ofNullable(e.getExpectedMethod()).map(ResolutionMethod::valueOf),
                Optional.ofNullable(e.getExpectedReason()).map(NotCoveredReason::valueOf));
        return new DemoAddress(e.getId(), e.getLabel(), e.getStreet(), e.getNumber(),
                new ComunaCode(e.getComunaCode()), e.getComunaName(), point, expected);
    }
}
