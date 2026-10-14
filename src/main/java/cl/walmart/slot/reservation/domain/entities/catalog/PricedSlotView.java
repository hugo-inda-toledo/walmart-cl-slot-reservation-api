package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotId;
import cl.walmart.slot.reservation.domain.entities.catalog.Money;
import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import java.time.Instant;

public record PricedSlotView(DeliverySlotId slotId, Instant startAt, Instant endAt, Money price,
                             int remainingCapacity, SlotStatus status) { }
