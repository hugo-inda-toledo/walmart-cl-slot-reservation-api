package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DeliverySlotEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeliverySlotJpaRepository extends JpaRepository<DeliverySlotEntity, UUID> {
    List<DeliverySlotEntity> findAllByZoneIdAndDeliveryDateBetweenOrderByDeliveryDateAscStartAtAscEndAtAsc(
            UUID zoneId, LocalDate from, LocalDate to);
}
