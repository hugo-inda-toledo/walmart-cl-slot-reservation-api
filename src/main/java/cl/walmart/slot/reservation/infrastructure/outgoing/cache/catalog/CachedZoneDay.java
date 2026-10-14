package cl.walmart.slot.reservation.infrastructure.outgoing.cache.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlot;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDayAvailability;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Representación JSON v1: conserva datos crudos, nunca estados derivados ni precios de políticas. */
record CachedZoneDay(UUID zoneId, LocalDate date, boolean closed, List<CachedSlot> slots) {
    static CachedZoneDay from(ZoneDayAvailability day) {
        return new CachedZoneDay(day.zoneId().value(), day.date(), day.closed(), day.slots().stream().map(CachedSlot::from).toList());
    }
    ZoneDayAvailability toDomain() {
        CatalogZoneId zone = new CatalogZoneId(zoneId);
        return new ZoneDayAvailability(zone, date, closed, slots.stream().map(slot -> slot.toDomain(zone, date)).toList());
    }
    record CachedSlot(UUID id, Instant startAt, Instant endAt, Instant cutoffAt, long basePriceClp, int capacity, int reserved, int held) {
        static CachedSlot from(DeliverySlot slot) { return new CachedSlot(slot.id().value(), slot.startAt(), slot.endAt(), slot.cutoffAt(), slot.basePrice().amountClp(), slot.capacity(), slot.reserved(), slot.held()); }
        DeliverySlot toDomain(CatalogZoneId zone, LocalDate date) { return new DeliverySlot(new DeliverySlotId(id), zone, date, startAt, endAt, cutoffAt, new Money(basePriceClp), capacity, reserved, held); }
    }
}
