package cl.walmart.slot.reservation.infrastructure.ingoing.controller.catalog;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.catalog.CatalogWebMapper;
import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.usecase.catalog.ListAvailableDatesUseCase;
import cl.walmart.slot.reservation.domain.usecase.catalog.ListSlotsForDateUseCase;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.api.CatalogApi;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliveryDatesDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.CatalogDeliverySlotsDto;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador HTTP del catálogo: traduce parámetros a consultas y vistas a DTOs. */
@RestController
public class DeliveryAvailabilityController implements CatalogApi {

    private final ListAvailableDatesUseCase listAvailableDates;
    private final ListSlotsForDateUseCase listSlotsForDate;
    private final CatalogWebMapper mapper;

    public DeliveryAvailabilityController(ListAvailableDatesUseCase listAvailableDates,
                                          ListSlotsForDateUseCase listSlotsForDate, CatalogWebMapper mapper) {
        this.listAvailableDates = listAvailableDates;
        this.listSlotsForDate = listSlotsForDate;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CatalogDeliveryDatesDto> listDeliveryDates(String comunaCode, Double latitude, Double longitude) {
        return ResponseEntity.ok(mapper.toDto(listAvailableDates.list(new AddressQuery(comunaCode, latitude, longitude))));
    }

    @Override
    public ResponseEntity<CatalogDeliverySlotsDto> listDeliverySlots(LocalDate date, String comunaCode,
                                                                           Double latitude, Double longitude) {
        return ResponseEntity.ok(mapper.toDto(listSlotsForDate.list(new AddressQuery(comunaCode, latitude, longitude), date)));
    }
}
