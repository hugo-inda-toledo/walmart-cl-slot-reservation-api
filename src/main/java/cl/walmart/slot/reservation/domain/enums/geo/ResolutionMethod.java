package cl.walmart.slot.reservation.domain.enums.geo;

/**
 * Regla que determinó la comuna de una dirección.
 */
public enum ResolutionMethod {
    ZONE_AREA,
    COMUNA_BOUNDARY,
    NEAREST_COMUNA,
    COMUNA_CODE
}
