package cl.walmart.slot.reservation.domain.usecase.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliveryDatesView;

public interface ListAvailableDatesUseCase { DeliveryDatesView list(AddressQuery query); }
