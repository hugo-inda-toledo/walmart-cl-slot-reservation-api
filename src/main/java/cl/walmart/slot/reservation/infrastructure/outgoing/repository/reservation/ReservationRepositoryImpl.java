package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reservas en PostgreSQL. La confirmación y la cancelación llevan su marca de tiempo y su guarda de cierre con el
 * {@code now()} de la base (nunca con el reloj de la JVM), cada transición es un único {@code UPDATE} condicional
 * y toda consulta o transición filtra por cliente.
 */
@Repository
class ReservationRepositoryImpl implements ReservationRepository {

    private static final String COLUMNS = "id, hold_id, customer_id, order_id, slot_id, zone_id, delivery_date, "
            + "start_at, end_at, cutoff_at, price_clp, status, confirmed_at, cancelled_at";

    private static final String INSERT_CONFIRMED = "INSERT INTO reservation_booking (id, hold_id, customer_id, "
            + "order_id, slot_id, zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, status, "
            + "confirmed_at) VALUES (:id, :hold, :customer, :order, :slot, :zone, :date, :startAt, :endAt, "
            + ":cutoffAt, :price, 'CONFIRMED', now()) "
            + "ON CONFLICT (customer_id, order_id) WHERE status = 'CONFIRMED' DO NOTHING RETURNING " + COLUMNS;

    private static final String FIND_FOR_CUSTOMER = "SELECT " + COLUMNS + ", now() AS db_now "
            + "FROM reservation_booking WHERE id = :id AND customer_id = :customer";

    private static final String FIND_CONFIRMED_FOR_ORDER = "SELECT " + COLUMNS + " FROM reservation_booking "
            + "WHERE customer_id = :customer AND order_id = :order AND status = 'CONFIRMED'";

    private static final String MARK_CANCELLED = "UPDATE reservation_booking "
            + "SET status = 'CANCELLED', cancelled_at = now() "
            + "WHERE id = :id AND customer_id = :customer AND status = 'CONFIRMED' AND cutoff_at > now() "
            + "RETURNING " + COLUMNS;

    private final JdbcClient jdbc;

    ReservationRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Reservation> insertConfirmed(ReservationId id, SlotHold confirmedHold) {
        SlotSnapshot slot = confirmedHold.slot();
        return jdbc.sql(INSERT_CONFIRMED)
                .param("id", id.value())
                .param("hold", confirmedHold.id().value())
                .param("customer", confirmedHold.customerId().value())
                .param("order", confirmedHold.orderId().value())
                .param("slot", slot.slotId().value())
                .param("zone", slot.zoneId())
                .param("date", slot.deliveryDate())
                .param("startAt", OffsetDateTime.ofInstant(slot.startAt(), ZoneOffset.UTC))
                .param("endAt", OffsetDateTime.ofInstant(slot.endAt(), ZoneOffset.UTC))
                .param("cutoffAt", OffsetDateTime.ofInstant(slot.cutoffAt(), ZoneOffset.UTC))
                .param("price", slot.priceClp())
                .query(ReservationRepositoryImpl::reservation).optional();
    }

    @Override
    public Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer) {
        return jdbc.sql(FIND_FOR_CUSTOMER).param("id", id.value()).param("customer", customer.value())
                .query((rs, row) -> new ReservationRecord(reservation(rs, row), instant(rs, "db_now")))
                .optional();
    }

    @Override
    public Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order) {
        return jdbc.sql(FIND_CONFIRMED_FOR_ORDER).param("customer", customer.value()).param("order", order.value())
                .query(ReservationRepositoryImpl::reservation).optional();
    }

    @Override
    public Optional<Reservation> markCancelled(ReservationId id, CustomerId customer) {
        return jdbc.sql(MARK_CANCELLED).param("id", id.value()).param("customer", customer.value())
                .query(ReservationRepositoryImpl::reservation).optional();
    }

    private static Reservation reservation(ResultSet rs, int row) throws SQLException {
        SlotSnapshot snapshot = new SlotSnapshot(new SlotId(rs.getObject("slot_id", UUID.class)),
                rs.getObject("zone_id", UUID.class), rs.getObject("delivery_date", LocalDate.class),
                instant(rs, "start_at"), instant(rs, "end_at"), instant(rs, "cutoff_at"), rs.getLong("price_clp"));
        OffsetDateTime cancelledAt = rs.getObject("cancelled_at", OffsetDateTime.class);
        return new Reservation(new ReservationId(rs.getObject("id", UUID.class)),
                new HoldId(rs.getObject("hold_id", UUID.class)), new CustomerId(rs.getString("customer_id")),
                new OrderId(rs.getString("order_id")), snapshot, ReservationStatus.valueOf(rs.getString("status")),
                instant(rs, "confirmed_at"), cancelledAt == null ? null : cancelledAt.toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
