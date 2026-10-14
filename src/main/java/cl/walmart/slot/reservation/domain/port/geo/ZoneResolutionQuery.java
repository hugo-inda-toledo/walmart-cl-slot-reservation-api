package cl.walmart.slot.reservation.domain.port.geo;

/**
 * Consulta de resolución: comuna declarada (CUT de 5 dígitos) y coordenadas opcionales, ambas o ninguna.
 */
public record ZoneResolutionQuery(String comunaCode, Double latitude, Double longitude) {
}
