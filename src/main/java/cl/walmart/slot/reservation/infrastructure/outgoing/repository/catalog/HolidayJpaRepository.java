package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.infrastructure.outgoing.repository.model.HolidayEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HolidayJpaRepository extends JpaRepository<HolidayEntity, java.util.UUID> {
    List<HolidayEntity> findAllByHolidayDateBetween(LocalDate from, LocalDate to);
}
