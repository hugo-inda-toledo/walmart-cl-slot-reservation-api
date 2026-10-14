package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Un punto dentro de un área de inclusión activa se asigna a su zona; la comuna es la del punto o la declarada. */
@Component
@Order(200)
public class InclusionAreaResolver implements CoverageResolver {

    private final ZoneLookupRepository zoneRepository;

    public InclusionAreaResolver(ZoneLookupRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @Override
    public ResolverDecision decide(ResolutionContext context) {
        return context.point()
                .flatMap(point -> zoneRepository.findActiveAreaCovering(point, ZoneAreaKind.INCLUSION))
                .<ResolverDecision>map(zone -> new ResolverDecision.ZoneMatched(zone,
                        context.comunaAtPoint().orElse(context.declaredComuna())))
                .orElseGet(ResolverDecision.Pass::new);
    }
}
