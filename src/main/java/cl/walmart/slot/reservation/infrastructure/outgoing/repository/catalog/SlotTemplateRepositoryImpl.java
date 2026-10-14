package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.Capacity;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.enums.catalog.ServiceType;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplate;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotTemplateId;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotTemplateRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.TimeRange;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lee las plantillas por JDBC y no por JPA: con {@code hibernate.jdbc.time_zone=UTC}, Hibernate desplaza las
 * columnas {@code time} según la zona de la JVM (16:00 se leía 13:00 en un host en America/Santiago), y la
 * hora local de la plantilla debe permanecer intacta.
 */
@Repository
class SlotTemplateRepositoryImpl implements SlotTemplateRepository {
    private final JdbcClient jdbc;

    SlotTemplateRepositoryImpl(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public List<SlotTemplate> findAllActive() {
        return jdbc.sql("""
                        select id, zone_id, day_of_week, start_time, end_time, capacity, price_clp, service_type
                        from catalog_slot_template where status = 'ACTIVE'
                        order by zone_id, day_of_week, start_time""")
                .query((rs, rowNum) -> new SlotTemplate(new SlotTemplateId(rs.getObject("id", UUID.class)),
                        new CatalogZoneId(rs.getObject("zone_id", UUID.class)), DayOfWeek.of(rs.getInt("day_of_week")),
                        new TimeRange(rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class)),
                        new Capacity(rs.getInt("capacity")), new Money(rs.getLong("price_clp")),
                        ServiceType.valueOf(rs.getString("service_type")), true))
                .list();
    }
}
