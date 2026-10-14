package cl.walmart.slot.reservation.infrastructure.outgoing.repository.model;

/**
 * Proyección de las consultas espaciales nativas sobre {@code geo_comuna}.
 */
public interface ComunaRow {

    String getCode();

    String getName();

    String getProvinceCode();

    String getProvinceName();

    boolean getHasBoundary();
}
