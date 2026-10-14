package cl.walmart.slot.reservation.domain.entities.catalog;

/** Datos mínimos de dirección que el catálogo delega al contexto geográfico. */
public record AddressQuery(String comunaCode, Double latitude, Double longitude) {
}
