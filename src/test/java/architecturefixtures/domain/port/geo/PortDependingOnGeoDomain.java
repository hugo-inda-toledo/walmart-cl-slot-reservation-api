package architecturefixtures.domain.port.geo;

import cl.walmart.slot.reservation.domain.entities.geo.ComunaCode;

/**
 * Contraejemplo: una clase del paquete del puerto publicado de geo que expone un tipo del dominio de geo.
 */
public interface PortDependingOnGeoDomain {

    ComunaCode comunaCode();
}
