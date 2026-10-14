package cl.walmart.slot.reservation.infrastructure.ingoing.controller.geo;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo.GeoJsonGeometryMapper;
import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo.GeoWebMapper;
import cl.walmart.slot.reservation.domain.usecase.geo.GetDeliveryZoneUseCase;
import cl.walmart.slot.reservation.domain.usecase.geo.ListDeliveryZonesUseCase;
import cl.walmart.slot.reservation.domain.entities.geo.ZoneDetail;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZoneId;
import cl.walmart.slot.reservation.domain.enums.geo.ZoneStatus;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.api.DeliveryZonesApi;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDetailDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoZoneStatusDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de zonas de despacho y su geometría (solo lectura). Controller delgado sobre los casos de uso.
 */
@RestController
public class DeliveryZoneController implements DeliveryZonesApi {

    private final ListDeliveryZonesUseCase listDeliveryZones;
    private final GetDeliveryZoneUseCase getDeliveryZone;
    private final GeoWebMapper mapper;
    private final GeoJsonGeometryMapper geometryMapper;

    public DeliveryZoneController(ListDeliveryZonesUseCase listDeliveryZones,
                                  GetDeliveryZoneUseCase getDeliveryZone, GeoWebMapper mapper,
                                  GeoJsonGeometryMapper geometryMapper) {
        this.listDeliveryZones = listDeliveryZones;
        this.getDeliveryZone = getDeliveryZone;
        this.mapper = mapper;
        this.geometryMapper = geometryMapper;
    }

    @Override
    public ResponseEntity<List<GeoDeliveryZoneDto>> listDeliveryZones(GeoZoneStatusDto status) {
        Optional<ZoneStatus> filter = Optional.ofNullable(status).map(s -> ZoneStatus.valueOf(s.name()));
        return ResponseEntity.ok(mapper.toZoneDtos(listDeliveryZones.list(filter)));
    }

    @Override
    public ResponseEntity<GeoDeliveryZoneDetailDto> getDeliveryZone(UUID zoneId, Boolean includeGeometry) {
        ZoneDetail detail = getDeliveryZone.get(new DeliveryZoneId(zoneId), Boolean.TRUE.equals(includeGeometry));
        GeoDeliveryZoneDetailDto dto = mapper.toDetailDto(detail.zone());
        detail.geometry().map(geometryMapper::toDto).ifPresent(dto::setGeometry);
        return ResponseEntity.ok(dto);
    }
}
