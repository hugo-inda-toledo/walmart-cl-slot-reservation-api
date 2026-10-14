package cl.walmart.slot.reservation.application.service.geo.resolver;

/**
 * Regla de resolución de cobertura. Las reglas se evalúan por orden ({@code @Order}) hasta la primera
 * decisión distinta de {@link ResolverDecision.Pass}; agregar una regla no modifica las existentes.
 */
public interface CoverageResolver {

    ResolverDecision decide(ResolutionContext context);
}
