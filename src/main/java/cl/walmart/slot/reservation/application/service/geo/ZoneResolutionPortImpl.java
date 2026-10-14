package cl.walmart.slot.reservation.application.service.geo;

import cl.walmart.slot.reservation.domain.service.geo.CoverageService;
import cl.walmart.slot.reservation.domain.port.geo.ZoneReference;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolution;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionPort;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionQuery;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.enums.geo.NotCoveredReason;
import cl.walmart.slot.reservation.domain.enums.geo.ResolutionMethod;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capa anticorrupción: traduce la consulta publicada a {@link Address} y el resultado de dominio a tipos del
 * puerto. Se une a la transacción del llamador sin marcarla rollback-only ante errores de entrada.
 */
@Service
@Transactional(readOnly = true, noRollbackFor = DomainException.class)
class ZoneResolutionPortImpl implements ZoneResolutionPort {

    /** La calle y el número no intervienen en la resolución (5.13); se usan valores sintéticos. */
    private static final String UNUSED = "-";

    private final CoverageService coverageService;
    private final ZoneLookupRepository zoneRepository;

    ZoneResolutionPortImpl(CoverageService coverageService, ZoneLookupRepository zoneRepository) {
        this.coverageService = coverageService;
        this.zoneRepository = zoneRepository;
    }

    @Override
    public ZoneResolution resolveZone(ZoneResolutionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.comunaCode() == null || query.comunaCode().isBlank()) {
            throw GeoException.unknownComuna(query.comunaCode());
        }
        var address = Address.of(UNUSED, UNUSED, query.comunaCode(), query.latitude(), query.longitude());
        return toResolution(coverageService.resolve(address));
    }

    @Override
    public Optional<ZoneReference> findZone(UUID zoneId) {
        return zoneRepository.findById(new DeliveryZoneId(zoneId))
                .map(zone -> new ZoneReference(zone.id().value(), zone.code(), zone.name(), zone.isActive()));
    }

    private static ZoneResolution toResolution(CoverageResult result) {
        return switch (result) {
            case CoverageResult.Covered c -> new ZoneResolution.Covered(c.zone().id().value(), c.zone().code(),
                    c.zone().name(), c.comuna().code().value(), c.comuna().name(), toMethod(c.method()),
                    c.comunaDiffersFromDeclared());
            case CoverageResult.NotCovered n -> new ZoneResolution.NotCovered(toReason(n.reason()),
                    n.comuna().map(Comuna::code).map(code -> code.value()));
        };
    }

    private static ZoneResolution.Method toMethod(ResolutionMethod method) {
        return switch (method) {
            case ZONE_AREA -> ZoneResolution.Method.ZONE_AREA;
            case COMUNA_BOUNDARY -> ZoneResolution.Method.COMUNA_BOUNDARY;
            case NEAREST_COMUNA -> ZoneResolution.Method.NEAREST_COMUNA;
            case COMUNA_CODE -> ZoneResolution.Method.COMUNA_CODE;
        };
    }

    private static ZoneResolution.Reason toReason(NotCoveredReason reason) {
        return switch (reason) {
            case EXCLUDED_AREA -> ZoneResolution.Reason.EXCLUDED_AREA;
            case OUTSIDE_SERVICE_AREA -> ZoneResolution.Reason.OUTSIDE_SERVICE_AREA;
            case COMUNA_NOT_SERVED -> ZoneResolution.Reason.COMUNA_NOT_SERVED;
            case ZONE_INACTIVE -> ZoneResolution.Reason.ZONE_INACTIVE;
        };
    }
}
