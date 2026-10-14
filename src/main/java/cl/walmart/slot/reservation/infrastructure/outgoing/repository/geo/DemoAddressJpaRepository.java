package cl.walmart.slot.reservation.infrastructure.outgoing.repository.geo;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DemoAddressEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface DemoAddressJpaRepository extends JpaRepository<DemoAddressEntity, String> {

    List<DemoAddressEntity> findAllByOrderBySortOrderAsc();
}
