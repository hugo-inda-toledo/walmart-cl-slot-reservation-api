package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DeliveryZoneEntity;
import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.entities.geo.DispatchNode;
import cl.walmart.slot.reservation.domain.entities.geo.GeoPoint;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ZoneLookupRepositoryImpl implements ZoneLookupRepository {

    private final DeliveryZoneJpaRepository jpaRepository;

    ZoneLookupRepositoryImpl(DeliveryZoneJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DeliveryZone> findById(DeliveryZoneId id) {
        return jpaRepository.findById(id.value()).map(ZoneLookupRepositoryImpl::toDomain);
    }

    @Override
    public List<DeliveryZone> findAll(Optional<ZoneStatus> status) {
        List<DeliveryZoneEntity> entities = status
                .map(s -> jpaRepository.findAllByStatusOrderByCodeAsc(s.name()))
                .orElseGet(jpaRepository::findAllByOrderByCodeAsc);
        return entities.stream().map(ZoneLookupRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<DeliveryZone> findAssignedTo(ComunaCode comuna) {
        return jpaRepository.findAssignedTo(comuna.value()).map(ZoneLookupRepositoryImpl::toDomain);
    }

    @Override
    public Optional<DeliveryZone> findActiveAreaCovering(GeoPoint point, ZoneAreaKind kind) {
        return jpaRepository.findActiveAreaCovering(point.latitude(), point.longitude(), kind.name())
                .flatMap(jpaRepository::findById)
                .map(ZoneLookupRepositoryImpl::toDomain);
    }

    @Override
    public Optional<ZoneGeometry> findGeometry(DeliveryZoneId id) {
        return jpaRepository.findGeometryGeoJson(id.value()).map(ZoneGeometry::new);
    }

    private static DeliveryZone toDomain(DeliveryZoneEntity entity) {
        return new DeliveryZone(new DeliveryZoneId(entity.getId()), entity.getCode(), entity.getName(),
                ZoneStatus.valueOf(entity.getStatus()),
                new DispatchNode(entity.getNode().getCode(), entity.getNode().getName()),
                entity.getComunas().stream().map(ComunaLookupRepositoryImpl::toDomain).toList(),
                entity.getInclusionAreaCount(), entity.getExclusionAreaCount());
    }
}
