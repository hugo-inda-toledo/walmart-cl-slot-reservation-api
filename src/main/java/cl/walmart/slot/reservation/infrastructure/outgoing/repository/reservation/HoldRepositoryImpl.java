package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Retenciones en PostgreSQL. Toda guarda de estado y de vencimiento se evalúa en la base con su {@code now()}
 * (nunca con el reloj de la JVM), cada transición es un único {@code UPDATE} condicional y toda consulta o
 * transición filtra por cliente. El lote de vencidas bloquea con {@code FOR UPDATE SKIP LOCKED}.
 */
@Repository
class HoldRepositoryImpl implements HoldRepository {

    private static final String RETURNING = " RETURNING id, customer_id, order_id, slot_id, zone_id, delivery_date, "
            + "start_at, end_at, cutoff_at, price_clp, status, created_at, expires_at, closed_at";

    private static final String INSERT_ACTIVE = "INSERT INTO reservation_slot_hold (id, customer_id, order_id, "
            + "slot_id, zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, status, created_at, "
            + "expires_at) VALUES (:id, :customer, :order, :slot, :zone, :date, :startAt, :endAt, :cutoffAt, "
            + ":price, 'ACTIVE', now(), now() + make_interval(secs => :ttlSeconds)) "
            + "ON CONFLICT (customer_id, order_id) WHERE status = 'ACTIVE' DO NOTHING" + RETURNING;

    private static final String SELECT_WITH_RESERVATION = "SELECT h.id, h.customer_id, h.order_id, h.slot_id, "
            + "h.zone_id, h.delivery_date, h.start_at, h.end_at, h.cutoff_at, h.price_clp, h.status, h.created_at, "
            + "h.expires_at, h.closed_at, b.id AS reservation_id, now() AS db_now "
            + "FROM reservation_slot_hold h LEFT JOIN reservation_booking b ON b.hold_id = h.id ";

    private static final String FIND_FOR_CUSTOMER = SELECT_WITH_RESERVATION
            + "WHERE h.id = :id AND h.customer_id = :customer";

    private static final String FIND_ACTIVE_FOR_ORDER = SELECT_WITH_RESERVATION
            + "WHERE h.customer_id = :customer AND h.order_id = :order AND h.status = 'ACTIVE'";

    private static final String EXPIRE_STALE_FOR_ORDER = "UPDATE reservation_slot_hold "
            + "SET status = 'EXPIRED', closed_at = now() "
            + "WHERE customer_id = :customer AND order_id = :order AND status = 'ACTIVE' AND expires_at <= now()"
            + RETURNING;

    private static final String MARK_CONFIRMED = "UPDATE reservation_slot_hold "
            + "SET status = 'CONFIRMED', closed_at = now() "
            + "WHERE id = :id AND customer_id = :customer AND status = 'ACTIVE' AND expires_at > now()" + RETURNING;

    private static final String MARK_RELEASED = "UPDATE reservation_slot_hold "
            + "SET status = 'RELEASED', closed_at = now() "
            + "WHERE id = :id AND customer_id = :customer AND status = 'ACTIVE'" + RETURNING;

    private static final String EXPIRE_DUE_BATCH = "WITH due AS (SELECT id FROM reservation_slot_hold "
            + "WHERE status = 'ACTIVE' AND expires_at <= now() ORDER BY expires_at LIMIT :limit "
            + "FOR UPDATE SKIP LOCKED) "
            + "UPDATE reservation_slot_hold SET status = 'EXPIRED', closed_at = now() "
            + "WHERE id IN (SELECT id FROM due)" + RETURNING;

    private final JdbcClient jdbc;

    HoldRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order, SlotSnapshot slot,
                                           Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return jdbc.sql(INSERT_ACTIVE)
                .param("id", id.value())
                .param("customer", customer.value())
                .param("order", order.value())
                .param("slot", slot.slotId().value())
                .param("zone", slot.zoneId())
                .param("date", slot.deliveryDate())
                .param("startAt", OffsetDateTime.ofInstant(slot.startAt(), java.time.ZoneOffset.UTC))
                .param("endAt", OffsetDateTime.ofInstant(slot.endAt(), java.time.ZoneOffset.UTC))
                .param("cutoffAt", OffsetDateTime.ofInstant(slot.cutoffAt(), java.time.ZoneOffset.UTC))
                .param("price", slot.priceClp())
                .param("ttlSeconds", ttl.toMillis() / 1000.0)
                .query(HoldRepositoryImpl::hold).optional();
    }

    @Override
    public Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer) {
        return jdbc.sql(FIND_FOR_CUSTOMER).param("id", id.value()).param("customer", customer.value())
                .query(HoldRepositoryImpl::record).optional();
    }

    @Override
    public Optional<HoldRecord> findActiveForOrder(CustomerId customer, OrderId order) {
        return jdbc.sql(FIND_ACTIVE_FOR_ORDER).param("customer", customer.value()).param("order", order.value())
                .query(HoldRepositoryImpl::record).optional();
    }

    @Override
    public Optional<SlotHold> expireStaleForOrder(CustomerId customer, OrderId order) {
        return jdbc.sql(EXPIRE_STALE_FOR_ORDER).param("customer", customer.value()).param("order", order.value())
                .query(HoldRepositoryImpl::hold).optional();
    }

    @Override
    public Optional<SlotHold> markConfirmed(HoldId id, CustomerId customer) {
        return jdbc.sql(MARK_CONFIRMED).param("id", id.value()).param("customer", customer.value())
                .query(HoldRepositoryImpl::hold).optional();
    }

    @Override
    public Optional<SlotHold> markReleased(HoldId id, CustomerId customer) {
        return jdbc.sql(MARK_RELEASED).param("id", id.value()).param("customer", customer.value())
                .query(HoldRepositoryImpl::hold).optional();
    }

    @Override
    public List<SlotHold> expireDueBatch(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.sql(EXPIRE_DUE_BATCH).param("limit", limit).query(HoldRepositoryImpl::hold).list();
    }

    private static HoldRecord record(ResultSet rs, int row) throws SQLException {
        return new HoldRecord(hold(rs, row),
                Optional.ofNullable(rs.getObject("reservation_id", UUID.class)).map(ReservationId::new),
                instant(rs, "db_now"));
    }

    private static SlotHold hold(ResultSet rs, int row) throws SQLException {
        SlotSnapshot snapshot = new SlotSnapshot(new SlotId(rs.getObject("slot_id", UUID.class)),
                rs.getObject("zone_id", UUID.class), rs.getObject("delivery_date", LocalDate.class),
                instant(rs, "start_at"), instant(rs, "end_at"), instant(rs, "cutoff_at"), rs.getLong("price_clp"));
        OffsetDateTime closedAt = rs.getObject("closed_at", OffsetDateTime.class);
        return new SlotHold(new HoldId(rs.getObject("id", UUID.class)),
                new CustomerId(rs.getString("customer_id")), new OrderId(rs.getString("order_id")), snapshot,
                HoldStatus.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "expires_at"),
                closedAt == null ? null : closedAt.toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
