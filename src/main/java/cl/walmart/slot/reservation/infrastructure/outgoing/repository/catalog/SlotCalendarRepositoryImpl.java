package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.NewDeliverySlot;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotCalendarRepository;
import cl.walmart.slot.reservation.domain.entities.catalog.SlotCalendarInsertion;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SlotCalendarRepositoryImpl implements SlotCalendarRepository {
    private final JdbcClient jdbc;
    SlotCalendarRepositoryImpl(JdbcClient jdbc) { this.jdbc = jdbc; }
    /** pgjdbc no infiere el tipo SQL de {@code Instant}; {@code timestamptz} se enlaza como {@code OffsetDateTime}. */
    private static java.time.OffsetDateTime utc(Instant instant) { return instant.atOffset(java.time.ZoneOffset.UTC); }
    @Override public SlotCalendarInsertion insertMissing(List<NewDeliverySlot> slots) {
        if (slots.isEmpty()) return SlotCalendarInsertion.empty();
        String values = String.join(", ", java.util.Collections.nCopies(slots.size(), "(?, ?, ?, ?, ?, ?, ?, ?, ?)"));
        List<Object> parameters = new java.util.ArrayList<>(slots.size() * 9);
        for (NewDeliverySlot slot : slots) {
            parameters.add(slot.templateId().value()); parameters.add(slot.zoneId().value()); parameters.add(slot.deliveryDate());
            parameters.add(slot.serviceType().name()); parameters.add(utc(slot.startAt())); parameters.add(utc(slot.endAt()));
            parameters.add(utc(slot.cutoffAt())); parameters.add(slot.price().amountClp()); parameters.add(slot.capacity().value());
        }
        Set<ZoneDate> created = new LinkedHashSet<>();
        var createdSlots = jdbc.sql("insert into catalog_delivery_slot (template_id, zone_id, delivery_date, service_type, start_at, end_at, cutoff_at, price_clp, capacity) values "
                        + values + " on conflict (template_id, delivery_date) do nothing returning zone_id, delivery_date")
                .params(parameters).query((resultSet, rowNum) -> new ZoneDate(
                        new CatalogZoneId(resultSet.getObject("zone_id", java.util.UUID.class)),
                        resultSet.getObject("delivery_date", LocalDate.class))).list();
        createdSlots.forEach(created::add);
        return new SlotCalendarInsertion(createdSlots.size(), created);
    }
    @Override public int closeRemainingCapacity(CatalogZoneId zone, LocalDate date, Instant now) {
        return jdbc.sql("update catalog_delivery_slot set capacity = reserved + held where zone_id = :zoneId and delivery_date = :date and cutoff_at > :now and capacity <> reserved + held")
                .param("zoneId", zone.value()).param("date", date).param("now", utc(now)).update();
    }
}
