package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Un punto fuera de todo límite se asigna a la comuna más cercana si está dentro de la tolerancia de borde. */
@Component
@Order(400)
public class NearestComunaResolver implements CoverageResolver {

    private final ComunaLookupRepository comunaRepository;
    private final BorderTolerance tolerance;

    public NearestComunaResolver(ComunaLookupRepository comunaRepository, BorderTolerance tolerance) {
        this.comunaRepository = comunaRepository;
        this.tolerance = tolerance;
    }

    @Override
    public ResolverDecision decide(ResolutionContext context) {
        return context.point()
                .flatMap(point -> comunaRepository.findNearestWithin(point, tolerance))
                .<ResolverDecision>map(comuna -> new ResolverDecision.ComunaMatched(comuna,
                        ResolutionMethod.NEAREST_COMUNA))
                .orElseGet(ResolverDecision.Pass::new);
    }
}
