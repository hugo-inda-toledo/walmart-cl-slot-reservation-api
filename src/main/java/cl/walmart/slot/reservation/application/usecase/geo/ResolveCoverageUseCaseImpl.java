package cl.walmart.slot.reservation.application.usecase.geo;

import cl.walmart.slot.reservation.domain.service.geo.CoverageService;
import cl.walmart.slot.reservation.domain.usecase.geo.ResolveCoverageUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.domain.exception.geo.AddressNotCoveredException;
import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ResolveCoverageUseCaseImpl implements ResolveCoverageUseCase {

    private final CoverageService coverageService;

    ResolveCoverageUseCaseImpl(CoverageService coverageService) {
        this.coverageService = coverageService;
    }

    @Override
    public CoverageResult.Covered resolve(Address address) {
        return switch (coverageService.resolve(address)) {
            case CoverageResult.Covered covered -> covered;
            case CoverageResult.NotCovered notCovered -> throw new AddressNotCoveredException(notCovered.reason(),
                    notCovered.comuna().map(Comuna::code));
        };
    }
}
