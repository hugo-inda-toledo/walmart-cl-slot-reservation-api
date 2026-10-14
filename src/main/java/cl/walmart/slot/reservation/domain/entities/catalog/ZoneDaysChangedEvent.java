package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.entities.catalog.ZoneDate;
import java.util.Set;

/** Cambio materializado cuya invalidez de caché se difiere hasta el commit. */
public record ZoneDaysChangedEvent(Set<ZoneDate> zoneDates) {
    public ZoneDaysChangedEvent { zoneDates = Set.copyOf(zoneDates); }
}
