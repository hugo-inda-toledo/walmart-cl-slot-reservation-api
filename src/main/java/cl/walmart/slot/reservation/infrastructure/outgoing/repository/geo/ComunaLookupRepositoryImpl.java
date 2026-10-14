package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.ComunaEntity;
import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.ComunaRow;
import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.entities.geo.ProvinceCode;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ComunaLookupRepositoryImpl implements ComunaLookupRepository {

    private final ComunaJpaRepository jpaRepository;

    ComunaLookupRepositoryImpl(ComunaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Comuna> findByCode(ComunaCode code) {
        return jpaRepository.findById(code.value()).map(ComunaLookupRepositoryImpl::toDomain);
    }

    @Override
    public List<Comuna> findByRegion(RegionCode code) {
        return jpaRepository.findByCodePrefix(code.value() + "%").stream()
                .map(ComunaLookupRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<Comuna> findCovering(GeoPoint point) {
        return jpaRepository.findCovering(point.latitude(), point.longitude())
                .map(ComunaLookupRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Comuna> findNearestWithin(GeoPoint point, BorderTolerance tolerance) {
        return jpaRepository.findNearestWithin(point.latitude(), point.longitude(), tolerance.meters())
                .map(ComunaLookupRepositoryImpl::toDomain);
    }

    static Comuna toDomain(ComunaEntity entity) {
        return new Comuna(new ComunaCode(entity.getCode()), entity.getName(),
                new ProvinceCode(entity.getProvince().getCode()), entity.getProvince().getName(),
                entity.isHasBoundary());
    }

    private static Comuna toDomain(ComunaRow row) {
        return new Comuna(new ComunaCode(row.getCode()), row.getName(), new ProvinceCode(row.getProvinceCode()),
                row.getProvinceName(), row.getHasBoundary());
    }
}
