package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo.GeoWebMapper;
import cl.walmart.slot.reservation.domain.usecase.geo.ListDemoAddressesUseCase;
import cl.walmart.slot.reservation.domain.usecase.geo.ResolveCoverageUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.Address;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.api.CoverageApi;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoCoverageRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoCoverageResolutionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDemoAddressDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolución de cobertura de una dirección y direcciones demo. Controller delgado sobre los casos de uso.
 */
@RestController
public class CoverageController implements CoverageApi {

    private final ResolveCoverageUseCase resolveCoverage;
    private final ListDemoAddressesUseCase listDemoAddresses;
    private final GeoWebMapper mapper;

    public CoverageController(ResolveCoverageUseCase resolveCoverage, ListDemoAddressesUseCase listDemoAddresses,
                              GeoWebMapper mapper) {
        this.resolveCoverage = resolveCoverage;
        this.listDemoAddresses = listDemoAddresses;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<GeoCoverageResolutionDto> resolveCoverage(GeoCoverageRequestDto request) {
        Address address = Address.of(request.getStreet(), request.getNumber(), request.getComunaCode(),
                request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(mapper.toDto(resolveCoverage.resolve(address)));
    }

    @Override
    public ResponseEntity<List<GeoDemoAddressDto>> listDemoAddresses() {
        return ResponseEntity.ok(mapper.toDemoAddressDtos(listDemoAddresses.list()));
    }
}
