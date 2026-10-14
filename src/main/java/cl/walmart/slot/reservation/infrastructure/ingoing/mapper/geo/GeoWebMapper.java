package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.geo;

import cl.walmart.slot.reservation.domain.entities.geo.Comuna;
import cl.walmart.slot.reservation.domain.entities.geo.CoverageResult;
import cl.walmart.slot.reservation.domain.entities.geo.DeliveryZone;
import cl.walmart.slot.reservation.domain.entities.geo.DemoAddress;
import cl.walmart.slot.reservation.domain.entities.geo.Region;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoComunaDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoComunaRefDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoCoverageResolutionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDetailDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDeliveryZoneDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDemoAddressDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoDispatchNodeDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoExpectedCoverageDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoNotCoveredReasonDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoRegionDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoResolutionMethodDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoZoneRefDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.GeoZoneStatusDto;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Traduce modelos de dominio de geo a los DTO generados del contrato.
 */
@Mapper
public interface GeoWebMapper {

    @Mapping(target = "code", source = "code.value")
    GeoRegionDto toDto(Region region);

    List<GeoRegionDto> toRegionDtos(List<Region> regions);

    @Mapping(target = "code", source = "code.value")
    @Mapping(target = "provinceCode", source = "provinceCode.value")
    GeoComunaDto toDto(Comuna comuna);

    List<GeoComunaDto> toComunaDtos(List<Comuna> comunas);

    default GeoCoverageResolutionDto toDto(CoverageResult.Covered covered) {
        return new GeoCoverageResolutionDto(
                toRef(covered.comuna()),
                toRef(covered.zone()),
                GeoResolutionMethodDto.valueOf(covered.method().name()),
                covered.comunaDiffersFromDeclared());
    }

    default GeoDemoAddressDto toDto(DemoAddress address) {
        var expected = address.expected();
        var expectedDto = new GeoExpectedCoverageDto(expected.covered());
        expectedDto.setZoneCode(expected.zoneCode().orElse(null));
        expectedDto.setMethod(expected.method().map(m -> GeoResolutionMethodDto.valueOf(m.name())).orElse(null));
        expectedDto.setReason(expected.reason().map(r -> GeoNotCoveredReasonDto.valueOf(r.name())).orElse(null));

        var dto = new GeoDemoAddressDto(address.id(), address.label(), address.street(), address.number(),
                new GeoComunaRefDto(address.comunaCode().value(), address.comunaName()), expectedDto);
        dto.setLatitude(address.point().map(p -> p.latitude()).orElse(null));
        dto.setLongitude(address.point().map(p -> p.longitude()).orElse(null));
        return dto;
    }

    List<GeoDemoAddressDto> toDemoAddressDtos(List<DemoAddress> addresses);

    default GeoDeliveryZoneDto toDto(DeliveryZone zone) {
        var dto = new GeoDeliveryZoneDto();
        dto.setId(zone.id().value());
        dto.setCode(zone.code());
        dto.setName(zone.name());
        dto.setStatus(GeoZoneStatusDto.valueOf(zone.status().name()));
        dto.setDispatchNode(new GeoDispatchNodeDto(zone.node().code(), zone.node().name()));
        dto.setComunas(zone.comunas().stream().map(GeoWebMapper::toRef).toList());
        return dto;
    }

    List<GeoDeliveryZoneDto> toZoneDtos(List<DeliveryZone> zones);

    /** Detalle sin geometría; el controller la agrega cuando existe. */
    default GeoDeliveryZoneDetailDto toDetailDto(DeliveryZone zone) {
        var dto = new GeoDeliveryZoneDetailDto();
        dto.setId(zone.id().value());
        dto.setCode(zone.code());
        dto.setName(zone.name());
        dto.setStatus(GeoZoneStatusDto.valueOf(zone.status().name()));
        dto.setDispatchNode(new GeoDispatchNodeDto(zone.node().code(), zone.node().name()));
        dto.setComunas(zone.comunas().stream().map(GeoWebMapper::toRef).toList());
        dto.setInclusionAreaCount(zone.inclusionAreaCount());
        dto.setExclusionAreaCount(zone.exclusionAreaCount());
        return dto;
    }

    private static GeoComunaRefDto toRef(Comuna comuna) {
        return new GeoComunaRefDto(comuna.code().value(), comuna.name());
    }

    private static GeoZoneRefDto toRef(DeliveryZone zone) {
        return new GeoZoneRefDto(zone.id().value(), zone.code(), zone.name());
    }
}
