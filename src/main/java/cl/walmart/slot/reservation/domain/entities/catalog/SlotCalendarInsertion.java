package cl.walmart.slot.reservation.domain.entities.catalog;

import java.util.Set;

/** Resultado de una inserción idempotente: ventanas creadas y las zona-fechas que requieren invalidación. */
public record SlotCalendarInsertion(int created, Set<ZoneDate> touched) {
    public SlotCalendarInsertion {
        if (created < 0) throw new IllegalArgumentException("created must not be negative");
        touched = Set.copyOf(touched);
    }
    public static SlotCalendarInsertion empty() { return new SlotCalendarInsertion(0, Set.of()); }
}
