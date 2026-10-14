package architecturefixtures.application.service.catalog;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;

/**
 * Contraejemplo: un contexto ajeno a geo que depende de un tipo interno del dominio de geo en lugar del puerto
 * publicado.
 */
public class CatalogDependingOnGeoInternals {

    private final ComunaCode comunaCode = new ComunaCode("13101");

    public ComunaCode comunaCode() {
        return comunaCode;
    }
}
