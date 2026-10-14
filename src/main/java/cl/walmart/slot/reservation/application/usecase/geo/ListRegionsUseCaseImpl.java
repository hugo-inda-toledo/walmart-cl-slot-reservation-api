package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.usecase.geo.ListRegionsUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.domain.repository.geo.RegionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ListRegionsUseCaseImpl implements ListRegionsUseCase {

    private final RegionRepository regionRepository;

    ListRegionsUseCaseImpl(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Override
    public List<Region> list() {
        return regionRepository.findAllOrderedNorthToSouth();
    }
}
