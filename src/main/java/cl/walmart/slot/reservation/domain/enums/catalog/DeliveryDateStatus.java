package cl.walmart.slot.reservation.domain.enums.catalog;

/**
 * Estado de una fecha de despacho para una zona, derivado de los estados de sus bloques.
 */
public enum DeliveryDateStatus {
    AVAILABLE,
    SOLD_OUT,
    NO_SERVICE
}
