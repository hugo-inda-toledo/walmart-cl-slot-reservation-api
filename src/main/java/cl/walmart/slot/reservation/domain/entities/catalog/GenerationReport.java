package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import java.time.LocalDate;
import java.util.Set;

public record GenerationReport(LocalDate from, LocalDate to, int created, Set<ZoneDate> touched) {
    public GenerationReport { touched = Set.copyOf(touched); }
}
