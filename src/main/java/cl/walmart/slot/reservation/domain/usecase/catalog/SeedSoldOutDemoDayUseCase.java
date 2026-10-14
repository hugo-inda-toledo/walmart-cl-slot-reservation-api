package cl.walmart.slot.reservation.domain.usecase.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import java.time.LocalDate;
import java.util.Optional;

public interface SeedSoldOutDemoDayUseCase {
    Optional<LocalDate> seed(CatalogZoneId zone, int offsetDays);
}
