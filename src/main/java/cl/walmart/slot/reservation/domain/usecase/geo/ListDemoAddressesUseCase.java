package cl.walmart.slot.reservation.domain.usecase.geo;

import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import java.util.List;

/**
 * Lista las direcciones de demostración con su resultado de cobertura esperado.
 */
public interface ListDemoAddressesUseCase {

    List<DemoAddress> list();
}
