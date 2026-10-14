package cl.walmart.slot.reservation.domain.port.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Vista estable consumida por reservas, sin exponer ningún tipo interno de catalog. */
public record SlotView(UUID slotId, UUID zoneId, LocalDate deliveryDate, Instant startAt, Instant endAt,
                       Instant cutoffAt, long priceClp, int capacity, int reserved, int held,
                       int remainingCapacity, Status status) {
    public enum Status { AVAILABLE, SOLD_OUT, CUTOFF_PASSED, NO_SERVICE }
}
