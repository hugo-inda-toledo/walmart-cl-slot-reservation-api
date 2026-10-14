package cl.walmart.slot.reservation.application.service.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.service.catalog.AddressZoneService;
import cl.walmart.slot.reservation.domain.entities.catalog.ResolvedZone;
import cl.walmart.slot.reservation.domain.exception.catalog.CatalogException;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolution;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionPort;
import cl.walmart.slot.reservation.domain.port.geo.ZoneResolutionQuery;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
class AddressZoneServiceImpl implements AddressZoneService {
    private final ZoneResolutionPort zoneResolutionPort;

    AddressZoneServiceImpl(ZoneResolutionPort zoneResolutionPort) {
        this.zoneResolutionPort = zoneResolutionPort;
    }

    @Override
    public ResolvedZone resolve(AddressQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return switch (zoneResolutionPort.resolveZone(new ZoneResolutionQuery(query.comunaCode(), query.latitude(),
                query.longitude()))) {
            case ZoneResolution.Covered covered -> new ResolvedZone(new CatalogZoneId(covered.zoneId()),
                    covered.zoneCode(), covered.zoneName(), covered.comunaCode(), covered.comunaName());
            case ZoneResolution.NotCovered uncovered -> throw CatalogException.addressNotCovered(uncovered.reason().name(),
                    uncovered.comunaCode().orElse(null));
        };
    }
}
