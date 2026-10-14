package cl.walmart.slot.reservation.domain.enums.catalog;

/**
 * Estado de un bloque horario. Precedencia: {@code CUTOFF_PASSED} > {@code SOLD_OUT} > {@code AVAILABLE}.
 */
public enum SlotStatus {
    AVAILABLE,
    SOLD_OUT,
    CUTOFF_PASSED
}
