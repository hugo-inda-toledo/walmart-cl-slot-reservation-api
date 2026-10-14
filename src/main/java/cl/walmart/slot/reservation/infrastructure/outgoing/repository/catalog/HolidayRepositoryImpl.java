package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.service.catalog.HolidayCalendar;
import cl.walmart.slot.reservation.domain.repository.catalog.HolidayRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
class HolidayRepositoryImpl implements HolidayRepository {
    private final HolidayJpaRepository jpaRepository;
    HolidayRepositoryImpl(HolidayJpaRepository jpaRepository) { this.jpaRepository = jpaRepository; }
    @Override public HolidayCalendar calendarBetween(LocalDate from, LocalDate to) {
        Set<LocalDate> national = new HashSet<>();
        Map<CatalogZoneId, Set<LocalDate>> blocks = new HashMap<>();
        jpaRepository.findAllByHolidayDateBetween(from, to).forEach(holiday -> {
            if (holiday.getZoneId() == null) national.add(holiday.getHolidayDate());
            else blocks.computeIfAbsent(new CatalogZoneId(holiday.getZoneId()), ignored -> new HashSet<>())
                    .add(holiday.getHolidayDate());
        });
        return new HolidayCalendar(national, blocks);
    }
}
