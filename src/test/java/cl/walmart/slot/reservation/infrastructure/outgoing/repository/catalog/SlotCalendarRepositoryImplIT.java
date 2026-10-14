package cl.walmart.slot.reservation.infrastructure.outgoing.repository.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.Capacity;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.service.catalog.FixedLeadTimeCutoffPolicy;
import cl.walmart.slot.reservation.domain.entities.catalog.NewDeliverySlot;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotCalendarRepository;
import cl.walmart.slot.reservation.domain.repository.catalog.SlotTemplateRepository;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SlotCalendarRepositoryImplIT {

    @Autowired
    private SlotTemplateRepository templates;

    @Autowired
    private SlotCalendarRepository calendar;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void insertsOnlyMissingSlotsAndClosesRemainingCapacityOnlyOnce() {
        LocalDate date = LocalDate.of(2032, 1, 12);
        var template = templates.findAllActive().stream().filter(value -> value.appliesTo(date)).findFirst().orElseThrow();
        NewDeliverySlot slot = template.instantiate(date, new FixedLeadTimeCutoffPolicy(java.time.Duration.ofHours(2)));

        assertThat(calendar.insertMissing(List.of(slot)).touched()).hasSize(1);
        assertThat(calendar.insertMissing(List.of(slot)).touched()).isEmpty();
        assertThat(calendar.closeRemainingCapacity(slot.zoneId(), date, Instant.parse("2032-01-01T00:00:00Z"))).isOne();
        assertThat(calendar.closeRemainingCapacity(slot.zoneId(), date, Instant.parse("2032-01-01T00:00:00Z"))).isZero();
    }

    @Test
    void remainsIdempotentUnderConcurrentInsertionAndPreservesCapacityCountersAndVersion() throws Exception {
        LocalDate date = LocalDate.of(2032, 1, 5);
        var template = templates.findAllActive().stream().filter(value -> value.appliesTo(date)).findFirst().orElseThrow();
        NewDeliverySlot slot = template.instantiate(date, new FixedLeadTimeCutoffPolicy(java.time.Duration.ofHours(2)));
        try (var executor = Executors.newFixedThreadPool(6)) {
            executor.invokeAll(java.util.Collections.nCopies(6, (Callable<cl.walmart.slot.reservation.domain.entities.catalog.SlotCalendarInsertion>) () -> calendar.insertMissing(List.of(slot))));
        }
        assertThat(jdbc.queryForObject("select count(*) from catalog_delivery_slot where template_id = ?::uuid and delivery_date = ?::date", Integer.class, slot.templateId().value().toString(), date.toString())).isOne();
        jdbc.update("update catalog_delivery_slot set capacity = 9, reserved = 2, held = 3 where template_id = ?::uuid and delivery_date = ?::date", slot.templateId().value().toString(), date.toString());
        long versionBeforeClose = jdbc.queryForObject("select version from catalog_delivery_slot where template_id = ?::uuid and delivery_date = ?::date", Long.class, slot.templateId().value().toString(), date.toString());

        assertThat(calendar.insertMissing(List.of(slot)).touched()).isEmpty();
        assertThat(jdbc.queryForMap("select capacity, reserved, held from catalog_delivery_slot where template_id = ?::uuid and delivery_date = ?::date", slot.templateId().value().toString(), date.toString())).containsEntry("capacity", 9).containsEntry("reserved", 2).containsEntry("held", 3);
        assertThat(calendar.closeRemainingCapacity(slot.zoneId(), date, Instant.parse("2032-01-01T00:00:00Z"))).isOne();
        assertThat(calendar.closeRemainingCapacity(slot.zoneId(), date, Instant.parse("2032-01-01T00:00:00Z"))).isZero();
        assertThat(jdbc.queryForObject("select version from catalog_delivery_slot where template_id = ?::uuid and delivery_date = ?::date", Long.class, slot.templateId().value().toString(), date.toString())).isEqualTo(versionBeforeClose + 1);
    }
}
