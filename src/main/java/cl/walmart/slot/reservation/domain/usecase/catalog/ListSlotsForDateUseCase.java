package cl.walmart.slot.reservation.domain.usecase.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.AddressQuery;
import cl.walmart.slot.reservation.domain.entities.catalog.DeliverySlotsView;
import java.time.LocalDate;

public interface ListSlotsForDateUseCase { DeliverySlotsView list(AddressQuery query, LocalDate date); }
