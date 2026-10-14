package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.usecase.geo.GetDeliveryZoneUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneDetail;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.exception.geo.GeoException;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneGeometry;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class GetDeliveryZoneUseCaseImpl implements GetDeliveryZoneUseCase {

    private final ZoneLookupRepository zoneRepository;

    GetDeliveryZoneUseCaseImpl(ZoneLookupRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @Override
    public ZoneDetail get(DeliveryZoneId id, boolean includeGeometry) {
        DeliveryZone zone = zoneRepository.findById(id).orElseThrow(() -> GeoException.zoneNotFound(id));
        Optional<ZoneGeometry> geometry = includeGeometry ? zoneRepository.findGeometry(id) : Optional.empty();
        return new ZoneDetail(zone, geometry);
    }
}
