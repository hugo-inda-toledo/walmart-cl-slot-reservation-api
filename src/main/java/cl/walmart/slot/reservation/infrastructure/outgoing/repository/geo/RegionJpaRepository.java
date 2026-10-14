package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.RegionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegionJpaRepository extends JpaRepository<RegionEntity, String> {

    List<RegionEntity> findAllByOrderByNorthToSouthOrderAsc();
}
