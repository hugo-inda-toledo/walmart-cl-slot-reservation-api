package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Un punto dentro del límite de una comuna se resuelve a esa comuna. */
@Component
@Order(300)
public class ComunaBoundaryResolver implements CoverageResolver {

    @Override
    public ResolverDecision decide(ResolutionContext context) {
        return context.comunaAtPoint()
                .<ResolverDecision>map(comuna -> new ResolverDecision.ComunaMatched(comuna,
                        ResolutionMethod.COMUNA_BOUNDARY))
                .orElseGet(ResolverDecision.Pass::new);
    }
}
