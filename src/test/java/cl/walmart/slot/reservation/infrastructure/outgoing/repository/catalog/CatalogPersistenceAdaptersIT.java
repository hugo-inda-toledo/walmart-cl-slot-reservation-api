package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.repository.catalog.HolidayRepository;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotTemplateRepository;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CatalogPersistenceAdaptersIT {

    @Autowired
    private SlotTemplateRepository templates;

    @Autowired
    private HolidayRepository holidays;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void readsTheActiveDemoTemplatesAndTheNationalHolidayCalendar() {
        assertThat(templates.findAllActive()).hasSize(188).allMatch(template -> template.active());

        var calendar = holidays.calendarBetween(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
        assertThat(calendar.isClosed(templates.findAllActive().getFirst().zoneId(), LocalDate.of(2026, 10, 12))).isTrue();
    }

    @Test
    void excludesInactiveTemplatesAndScopesZoneBlocksWithoutHidingOtherZones() {
        var first = templates.findAllActive().getFirst();
        jdbc.update("insert into catalog_slot_template (id, zone_id, day_of_week, start_time, end_time, capacity, price_clp, service_type, status) values ('00000000-0000-4000-8003-0000000000ad'::uuid, ?::uuid, 1, '01:00', '02:00', 1, 1, 'HOME_DELIVERY', 'INACTIVE')", first.zoneId().value());
        jdbc.update("insert into catalog_holiday (holiday_date, name, zone_id) values ('2032-01-05', 'Bloqueo de prueba', ?::uuid)", first.zoneId().value());

        var calendar = holidays.calendarBetween(LocalDate.of(2032, 1, 5), LocalDate.of(2032, 1, 5));
        assertThat(templates.findAllActive()).allMatch(template -> template.active())
                .noneMatch(template -> template.id().value().toString().endsWith("00ad"));
        assertThat(calendar.isClosed(first.zoneId(), LocalDate.of(2032, 1, 5))).isTrue();
        assertThat(calendar.isClosed(templates.findAllActive().stream().filter(template -> !template.zoneId().equals(first.zoneId())).findFirst().orElseThrow().zoneId(), LocalDate.of(2032, 1, 5))).isFalse();
    }
}
