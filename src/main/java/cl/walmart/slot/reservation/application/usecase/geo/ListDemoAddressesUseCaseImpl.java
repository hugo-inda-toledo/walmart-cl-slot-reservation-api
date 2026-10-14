package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.usecase.geo.ListDemoAddressesUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import cl.walmart.slot.reservation.domain.repository.geo.DemoAddressRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ListDemoAddressesUseCaseImpl implements ListDemoAddressesUseCase {

    private final DemoAddressRepository demoAddressRepository;

    ListDemoAddressesUseCaseImpl(DemoAddressRepository demoAddressRepository) {
        this.demoAddressRepository = demoAddressRepository;
    }

    @Override
    public List<DemoAddress> list() {
        return demoAddressRepository.findAllOrdered();
    }
}
