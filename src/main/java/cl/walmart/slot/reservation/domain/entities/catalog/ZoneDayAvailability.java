package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.DeliveryDateStatus;
import cl.walmart.slot.reservation.domain.enums.catalog.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ventanas de una zona en una fecha, más el cierre por feriado o bloqueo. Es la unidad de cálculo del estado de
 * la fecha y de sus bloques, por lo que ambos son coherentes por construcción.
 */
public record ZoneDayAvailability(CatalogZoneId zoneId, LocalDate date, boolean closed, List<DeliverySlot> slots) {

    private static final Comparator<DeliverySlot> BY_START_THEN_END =
            Comparator.comparing(DeliverySlot::startAt).thenComparing(DeliverySlot::endAt);

    public ZoneDayAvailability {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
        if (slots.stream().anyMatch(slot -> !slot.zoneId().equals(zoneId))) {
            throw new IllegalArgumentException("every slot must belong to zoneId " + zoneId.value());
        }
        if (slots.stream().anyMatch(slot -> !slot.deliveryDate().equals(date))) {
            throw new IllegalArgumentException("every slot must belong to date " + date);
        }
    }

    /** Ventanas aún no iniciadas, ordenadas por inicio y término; vacía si la fecha está cerrada. */
    public List<DeliverySlot> visibleSlotsAt(Instant now) {
        if (closed) {
            return List.of();
        }
        return slots.stream()
                .filter(slot -> !slot.hasStartedAt(now))
                .sorted(BY_START_THEN_END)
                .toList();
    }

    /**
     * Cerrada o sin ventanas abiertas (visibles y antes de su cierre) ⇒ {@code NO_SERVICE}; alguna disponible ⇒
     * {@code AVAILABLE}; en otro caso {@code SOLD_OUT}.
     */
    public DeliveryDateStatus dateStatusAt(Instant now) {
        if (closed) {
            return DeliveryDateStatus.NO_SERVICE;
        }
        var open = visibleSlotsAt(now).stream().filter(slot -> !slot.isCutoffPassedAt(now)).toList();
        if (open.isEmpty()) {
            return DeliveryDateStatus.NO_SERVICE;
        }
        var anyAvailable = open.stream().anyMatch(slot -> slot.statusAt(now) == SlotStatus.AVAILABLE);
        return anyAvailable ? DeliveryDateStatus.AVAILABLE : DeliveryDateStatus.SOLD_OUT;
    }
}
