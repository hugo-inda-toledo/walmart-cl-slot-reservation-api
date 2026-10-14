package cl.walmart.slot.reservation.application.service.geo.resolver;

import cl.walmart.slot.reservation.domain.enums.geo.ZoneAreaKind;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Un punto dentro de un área de exclusión activa no tiene cobertura, aunque su comuna esté en una zona. */
@Component
@Order(100)
public class ExclusionAreaResolver implements CoverageResolver {

    private final ZoneLookupRepository zoneRepository;

    public ExclusionAreaResolver(ZoneLookupRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @Override
    public ResolverDecision decide(ResolutionContext context) {
        return context.point()
                .flatMap(point -> zoneRepository.findActiveAreaCovering(point, ZoneAreaKind.EXCLUSION))
                .<ResolverDecision>map(zone -> new ResolverDecision.Excluded())
                .orElseGet(ResolverDecision.Pass::new);
    }
}
