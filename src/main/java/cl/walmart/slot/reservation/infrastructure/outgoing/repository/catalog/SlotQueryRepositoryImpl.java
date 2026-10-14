package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.DeliverySlotEntity;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotQueryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class SlotQueryRepositoryImpl implements SlotQueryRepository {
    private final DeliverySlotJpaRepository jpaRepository;
    SlotQueryRepositoryImpl(DeliverySlotJpaRepository jpaRepository) { this.jpaRepository = jpaRepository; }
    @Override public List<DeliverySlot> findByZoneAndDates(CatalogZoneId zone, LocalDate from, LocalDate to) {
        return jpaRepository.findAllByZoneIdAndDeliveryDateBetweenOrderByDeliveryDateAscStartAtAscEndAtAsc(zone.value(), from, to)
                .stream().map(SlotQueryRepositoryImpl::toDomain).toList();
    }
    @Override public Optional<DeliverySlot> findById(DeliverySlotId id) { return jpaRepository.findById(id.value()).map(SlotQueryRepositoryImpl::toDomain); }
    private static DeliverySlot toDomain(DeliverySlotEntity entity) {
        return new DeliverySlot(new DeliverySlotId(entity.getId()), new CatalogZoneId(entity.getZoneId()), entity.getDeliveryDate(),
                entity.getStartAt(), entity.getEndAt(), entity.getCutoffAt(), new Money(entity.getPriceClp()), entity.getCapacity(), entity.getReserved(), entity.getHeld());
    }
}
