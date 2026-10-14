package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.usecase.geo.ListDeliveryZonesUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.repository.geo.ZoneLookupRepository;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ListDeliveryZonesUseCaseImpl implements ListDeliveryZonesUseCase {

    private final ZoneLookupRepository zoneRepository;

    ListDeliveryZonesUseCaseImpl(ZoneLookupRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @Override
    public List<DeliveryZone> list(Optional<ZoneStatus> status) {
        return zoneRepository.findAll(status);
    }
}
