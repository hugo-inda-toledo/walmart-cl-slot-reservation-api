package cl.walmart.slot.reservation.domain.entities.reservation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Vista de salida (serializable) del snapshot de la ventana. */
public record SlotSnapshotView(UUID slotId, UUID zoneId, LocalDate deliveryDate, Instant startAt, Instant endAt,
                               long priceClp) {
}
