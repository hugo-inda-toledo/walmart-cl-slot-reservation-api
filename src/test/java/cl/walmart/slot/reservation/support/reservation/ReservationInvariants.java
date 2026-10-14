package cl.walmart.slot.reservation.support.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Invariante de conservación entre catalog y reservas: por ventana, {@code held} = retenciones {@code ACTIVE} y
 * {@code reserved} = reservas {@code CONFIRMED}. Se lee en una sola sentencia para obtener una foto consistente.
 */
public final class ReservationInvariants {

    private final JdbcTemplate jdbc;

    public ReservationInvariants(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** Contadores y conteos de cada ventana pedida que exista; las inexistentes no aparecen. */
    public List<SlotCounters> countersOf(Collection<UUID> slotIds) {
        String[] ids = slotIds.stream().map(UUID::toString).toArray(String[]::new);
        return jdbc.query("select s.id, s.capacity, s.held, s.reserved, "
                        + "(select count(*) from reservation_slot_hold h "
                        + " where h.slot_id = s.id and h.status = 'ACTIVE') as active_holds, "
                        + "(select count(*) from reservation_booking b "
                        + " where b.slot_id = s.id and b.status = 'CONFIRMED') as confirmed_reservations "
                        + "from catalog_delivery_slot s where s.id = any (?::uuid[]) order by s.id",
                (rs, row) -> new SlotCounters(rs.getObject("id", UUID.class), rs.getInt("capacity"),
                        rs.getInt("held"), rs.getInt("reserved"), rs.getInt("active_holds"),
                        rs.getInt("confirmed_reservations")),
                (Object) ids);
    }

    /** Ventanas pedidas cuyo contador no coincide con sus filas; vacío si se conserva la invariante. */
    public List<SlotCounters> violationsOf(Collection<UUID> slotIds) {
        return countersOf(slotIds).stream().filter(counters -> !counters.conserved()).toList();
    }

    /** Falla si alguna ventana pedida no existe o no conserva sus contadores. */
    public void assertConserved(Collection<UUID> slotIds) {
        List<SlotCounters> counters = countersOf(slotIds);
        assertThat(counters).extracting(SlotCounters::slotId).containsExactlyInAnyOrderElementsOf(slotIds);
        assertThat(counters).as("held = retenciones ACTIVE y reserved = reservas CONFIRMED por ventana")
                .allMatch(SlotCounters::conserved);
    }

    public void assertConserved(UUID... slotIds) {
        assertConserved(List.of(slotIds));
    }

    public record SlotCounters(UUID slotId, int capacity, int held, int reserved, int activeHolds,
                               int confirmedReservations) {

        public boolean conserved() {
            return held == activeHolds && reserved == confirmedReservations;
        }
    }
}
