package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.domain.repository.geo.RegionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class RegionRepositoryImpl implements RegionRepository {

    private final RegionJpaRepository jpaRepository;

    RegionRepositoryImpl(RegionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Region> findAllOrderedNorthToSouth() {
        return jpaRepository.findAllByOrderByNorthToSouthOrderAsc().stream()
                .map(e -> new Region(new RegionCode(e.getCode()), e.getName(), e.getNorthToSouthOrder()))
                .toList();
    }

    @Override
    public boolean exists(RegionCode code) {
        return jpaRepository.existsById(code.value());
    }
}
