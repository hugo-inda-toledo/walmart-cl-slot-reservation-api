package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDateView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDatesView;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotsView;
import cl.walmart.slot.reservation.domain.entities.catalog.PricedSlotView;
import cl.walmart.slot.reservation.domain.entities.catalog.ResolvedZone;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogComunaRefDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliveryDateDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliveryDatesDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDateStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliverySlotsDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliverySlotDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogSlotStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogZoneRefDto;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.mapstruct.Mapper;

/** Traduce las vistas del catálogo a los DTOs del contrato HTTP. */
@Mapper
public interface CatalogWebMapper {

    ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    default CatalogDeliveryDatesDto toDto(DeliveryDatesView view) {
        return new CatalogDeliveryDatesDto(zone(view.zone()), comuna(view.zone()),
                CatalogDeliveryDatesDto.TimeZoneEnum.AMERICA_SANTIAGO, dateDtos(view.dates()));
    }

    default CatalogDeliverySlotsDto toDto(DeliverySlotsView view) {
        return new CatalogDeliverySlotsDto(zone(view.zone()), comuna(view.zone()), view.date().date(), view.date().label(),
                CatalogDateStatusDto.valueOf(view.date().status().name()),
                CatalogDeliverySlotsDto.TimeZoneEnum.AMERICA_SANTIAGO, slotDtos(view.slots()));
    }

    default List<CatalogDeliveryDateDto> dateDtos(List<DeliveryDateView> views) {
        return views.stream().map(this::dateDto).toList();
    }

    default CatalogDeliveryDateDto dateDto(DeliveryDateView view) {
        return new CatalogDeliveryDateDto(view.date(), view.label(), CatalogDateStatusDto.valueOf(view.status().name()));
    }

    default List<CatalogDeliverySlotDto> slotDtos(List<PricedSlotView> views) {
        return views.stream().map(this::slotDto).toList();
    }

    default CatalogDeliverySlotDto slotDto(PricedSlotView view) {
        return new CatalogDeliverySlotDto(view.slotId().value(), OffsetDateTime.ofInstant(view.startAt(), BUSINESS_ZONE),
                OffsetDateTime.ofInstant(view.endAt(), BUSINESS_ZONE), view.price().amountClp(), view.remainingCapacity(),
                CatalogSlotStatusDto.valueOf(view.status().name()));
    }

    private static CatalogZoneRefDto zone(ResolvedZone zone) {
        return new CatalogZoneRefDto(zone.zoneId().value(), zone.zoneCode(), zone.zoneName());
    }

    private static CatalogComunaRefDto comuna(ResolvedZone zone) {
        return new CatalogComunaRefDto(zone.comunaCode(), zone.comunaName());
    }
}
