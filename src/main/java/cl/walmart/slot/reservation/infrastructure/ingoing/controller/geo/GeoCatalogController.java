package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo.GeoWebMapper;
import cl.walmart.slot.reservation.domain.usecase.geo.ListComunasByRegionUseCase;
import cl.walmart.slot.reservation.domain.usecase.geo.ListRegionsUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.RegionCode;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.api.GeoApi;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoComunaDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoRegionDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo geográfico: regiones y comunas por región. Controller delgado sobre los casos de uso.
 */
@RestController
public class GeoCatalogController implements GeoApi {

    private final ListRegionsUseCase listRegions;
    private final ListComunasByRegionUseCase listComunasByRegion;
    private final GeoWebMapper mapper;

    public GeoCatalogController(ListRegionsUseCase listRegions, ListComunasByRegionUseCase listComunasByRegion,
                                GeoWebMapper mapper) {
        this.listRegions = listRegions;
        this.listComunasByRegion = listComunasByRegion;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<GeoRegionDto>> listRegions() {
        return ResponseEntity.ok(mapper.toRegionDtos(listRegions.list()));
    }

    @Override
    public ResponseEntity<List<GeoComunaDto>> listComunasByRegion(String regionCode) {
        return ResponseEntity.ok(mapper.toComunaDtos(listComunasByRegion.list(new RegionCode(regionCode))));
    }
}
