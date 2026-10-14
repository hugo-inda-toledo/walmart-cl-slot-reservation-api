package cl.walmart.slot.reservation.application.service.geo;

import cl.walmart.slot.reservation.domain.service.geo.CoverageService;
import cl.walmart.slot.reservation.application.service.geo.resolver.CoverageResolver;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolutionContext;
import cl.walmart.slot.reservation.application.service.geo.resolver.ResolverDecision;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.repository.geo.ComunaLookupRepository;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class CoverageServiceImpl implements CoverageService {

    private final ComunaLookupRepository comunaRepository;
    private final ZoneLookupRepository zoneRepository;
    private final List<CoverageResolver> resolvers;

    /** {@code resolvers} llega ordenada por {@code @Order}; se evalúa en ese orden. */
    CoverageServiceImpl(ComunaLookupRepository comunaRepository, ZoneLookupRepository zoneRepository,
            List<CoverageResolver> resolvers) {
        this.comunaRepository = comunaRepository;
        this.zoneRepository = zoneRepository;
        this.resolvers = List.copyOf(resolvers);
    }

    @Override
    public CoverageResult resolve(Address address) {
        Comuna declared = comunaRepository.findByCode(address.comunaCode())
                .orElseThrow(() -> GeoException.unknownComuna(address.comunaCode().value()));
        var context = new ResolutionContext(address, declared, comunaRepository);
        for (CoverageResolver resolver : resolvers) {
            ResolverDecision decision = resolver.decide(context);
            if (!(decision instanceof ResolverDecision.Pass)) {
                return toResult(decision, declared, context);
            }
        }
        return new CoverageResult.NotCovered(NotCoveredReason.OUTSIDE_SERVICE_AREA, Optional.empty());
    }

    private CoverageResult toResult(ResolverDecision decision, Comuna declared, ResolutionContext context) {
        return switch (decision) {
            case ResolverDecision.Excluded e ->
                    new CoverageResult.NotCovered(NotCoveredReason.EXCLUDED_AREA, context.comunaAtPoint());
            case ResolverDecision.ZoneMatched m -> new CoverageResult.Covered(m.comuna(), m.zone(),
                    ResolutionMethod.ZONE_AREA, differs(m.comuna(), declared));
            case ResolverDecision.ComunaMatched m -> assignZone(m.comuna(), m.method(), declared);
            case ResolverDecision.Pass p -> throw new IllegalStateException("Pass is not a final decision");
        };
    }

    private CoverageResult assignZone(Comuna comuna, ResolutionMethod method, Comuna declared) {
        return zoneRepository.findAssignedTo(comuna.code())
                .<CoverageResult>map(zone -> zone.isActive()
                        ? new CoverageResult.Covered(comuna, zone, method, differs(comuna, declared))
                        : new CoverageResult.NotCovered(NotCoveredReason.ZONE_INACTIVE, Optional.of(comuna)))
                .orElseGet(() -> new CoverageResult.NotCovered(NotCoveredReason.COMUNA_NOT_SERVED,
                        Optional.of(comuna)));
    }

    private static boolean differs(Comuna resolved, Comuna declared) {
        return !resolved.code().equals(declared.code());
    }
}
