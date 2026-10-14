package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Resuelve por el código de comuna declarado cuando no hay punto, o cuando la comuna declarada no tiene límite
 * (no se puede afirmar que el punto esté fuera de ella).
 */
@Component
@Order(500)
public class ComunaCodeResolver implements CoverageResolver {

    @Override
    public ResolverDecision decide(ResolutionContext context) {
        if (context.point().isEmpty() || !context.declaredComuna().hasBoundary()) {
            return new ResolverDecision.ComunaMatched(context.declaredComuna(), ResolutionMethod.COMUNA_CODE);
        }
        return new ResolverDecision.Pass();
    }
}
