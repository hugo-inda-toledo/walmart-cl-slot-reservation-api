package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CounterRelease;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.repository.reservation.SlotCapacityRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrituras atómicas de {@code reserved}/{@code held}. Cada sentencia es un único {@code UPDATE} condicional cuya
 * guarda se evalúa en la base (capacidad y cierre con su {@code now()}); la {@code CHECK} de catalog queda como
 * última defensa. Solo asigna los contadores: la versión la sube el trigger de la ventana.
 */
@Repository
class SlotCapacityRepositoryImpl implements SlotCapacityRepository {

    private static final String TRY_HOLD = "UPDATE catalog_delivery_slot SET held = held + 1 "
            + "WHERE id = :id AND reserved + held < capacity AND cutoff_at > now() "
            + "RETURNING zone_id, delivery_date";

    /** Distinción ante 0 filas: misma hora de la base para el cierre y para {@code SoldOut}. */
    private static final String DIAGNOSE = "SELECT cutoff_at, now() AS db_now, cutoff_at <= now() AS closed "
            + "FROM catalog_delivery_slot WHERE id = :id";

    private static final String MOVE_HELD_TO_RESERVED = "UPDATE catalog_delivery_slot "
            + "SET held = held - 1, reserved = reserved + 1 "
            + "WHERE id = :id AND held > 0 RETURNING zone_id, delivery_date";

    private static final String RELEASE_RESERVED = "UPDATE catalog_delivery_slot SET reserved = reserved - 1 "
            + "WHERE id = :id AND reserved > 0 RETURNING zone_id, delivery_date";

    private static final String RELEASE_HELD_PREFIX = "UPDATE catalog_delivery_slot s SET held = s.held - v.n "
            + "FROM (VALUES ";
    private static final String RELEASE_HELD_SUFFIX = ") AS v(id, n) "
            + "WHERE s.id = v.id AND s.held >= v.n RETURNING s.zone_id, s.delivery_date";

    private final JdbcClient jdbc;

    SlotCapacityRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HoldCapacityOutcome tryHold(SlotId slot) {
        Optional<ZoneDay> acquired = jdbc.sql(TRY_HOLD).param("id", slot.value())
                .query(SlotCapacityRepositoryImpl::zoneDay).optional();
        if (acquired.isPresent()) {
            return new HoldCapacityOutcome.Acquired(acquired.get());
        }
        return jdbc.sql(DIAGNOSE).param("id", slot.value())
                .query((rs, row) -> rs.getBoolean("closed")
                        ? (HoldCapacityOutcome) new HoldCapacityOutcome.CutoffPassed(
                                rs.getObject("cutoff_at", OffsetDateTime.class).toInstant())
                        : new HoldCapacityOutcome.SoldOut(rs.getObject("db_now", OffsetDateTime.class).toInstant()))
                .optional()
                .orElseGet(HoldCapacityOutcome.NotFound::new);
    }

    @Override
    public Optional<ZoneDay> moveHeldToReserved(SlotId slot) {
        return jdbc.sql(MOVE_HELD_TO_RESERVED).param("id", slot.value())
                .query(SlotCapacityRepositoryImpl::zoneDay).optional();
    }

    @Override
    public CounterRelease releaseHeld(Map<SlotId, Integer> countsBySlot) {
        if (countsBySlot.isEmpty()) {
            return new CounterRelease(Set.of(), 0);
        }
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<SlotId, Integer> entry : countsBySlot.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new IllegalArgumentException("count to release must be positive");
            }
            if (!params.isEmpty()) {
                values.append(", ");
            }
            values.append("(?::uuid, ?::integer)");
            params.add(entry.getKey().value());
            params.add(entry.getValue());
        }
        List<ZoneDay> touched = jdbc.sql(RELEASE_HELD_PREFIX + values + RELEASE_HELD_SUFFIX)
                .params(params).query(SlotCapacityRepositoryImpl::zoneDay).list();
        return new CounterRelease(new HashSet<>(touched), touched.size());
    }

    @Override
    public Optional<ZoneDay> releaseReserved(SlotId slot) {
        return jdbc.sql(RELEASE_RESERVED).param("id", slot.value())
                .query(SlotCapacityRepositoryImpl::zoneDay).optional();
    }

    private static ZoneDay zoneDay(ResultSet rs, int row) throws SQLException {
        return new ZoneDay(rs.getObject("zone_id", java.util.UUID.class),
                rs.getObject("delivery_date", java.time.LocalDate.class));
    }
}
