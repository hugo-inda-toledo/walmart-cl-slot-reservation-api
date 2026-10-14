package cl.walmart.slot.reservation.domain.port.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotQueryPort {
    Optional<SlotView> findSlot(UUID slotId);
    List<SlotView> findAvailableSlots(UUID zoneId, LocalDate from, LocalDate to);
}
