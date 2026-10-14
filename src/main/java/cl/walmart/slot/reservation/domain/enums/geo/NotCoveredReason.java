package cl.walmart.slot.reservation.domain.enums.geo;

/**
 * Motivo por el cual una dirección no tiene cobertura de despacho.
 */
public enum NotCoveredReason {
    EXCLUDED_AREA,
    OUTSIDE_SERVICE_AREA,
    COMUNA_NOT_SERVED,
    ZONE_INACTIVE
}
