package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class ReservationSchemaIT {

    private static final String ZONE = "00000000-0000-4000-8000-0000000000d1";
    private static final String TEMPLATE = "00000000-0000-4000-8003-0000000000d1";
    private static final String SLOT = "00000000-0000-4000-8004-0000000000d1";
    private static final String UNKNOWN_SLOT = "00000000-0000-4000-8004-0000000000ff";
    private static final String HOLD = "00000000-0000-4000-8005-0000000000d1";

    private static final String INSERT_HOLD = "insert into reservation_slot_hold (id, customer_id, order_id, "
            + "slot_id, zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, status, expires_at, "
            + "closed_at) values (gen_random_uuid(), ?, ?, ?::uuid, '" + ZONE + "'::uuid, '2031-04-07', "
            + "'2031-04-07T16:00:00-04:00', '2031-04-07T18:00:00-04:00', '2031-04-07T14:00:00-04:00', 2990, ?, "
            + "now() + interval '10 minutes', ?::timestamptz)";
    private static final String INSERT_BOOKING = "insert into reservation_booking (id, hold_id, customer_id, "
            + "order_id, slot_id, zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, status, "
            + "confirmed_at, cancelled_at) values (gen_random_uuid(), ?::uuid, ?, ?, ?::uuid, '" + ZONE + "'::uuid, "
            + "'2031-04-07', '2031-04-07T16:00:00-04:00', '2031-04-07T18:00:00-04:00', "
            + "'2031-04-07T14:00:00-04:00', 2990, ?, now(), ?::timestamptz)";
    private static final String INSERT_KEY = "insert into reservation_idempotency_key (customer_id, "
            + "idempotency_key, operation, request_fingerprint, outcome, expires_at) "
            + "values (?, ?, ?, repeat('a', 64), ?, now() + interval '1 day')";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void insertFixtureZoneTemplateAndSlot() {
        jdbc.update("insert into geo_dispatch_node (code, name) values ('CD-RSV', 'CD reservas')");
        jdbc.update("insert into geo_delivery_zone (id, code, name, status, dispatch_node_code) "
                + "values (?::uuid, 'RESERVATION_TEST', 'Zona reservas', 'ACTIVE', 'CD-RSV')", ZONE);
        jdbc.update("insert into catalog_slot_template (id, zone_id, day_of_week, start_time, end_time, capacity, "
                + "price_clp, service_type, status) values (?::uuid, ?::uuid, 1, '16:00', '18:00', 5, 2990, "
                + "'HOME_DELIVERY', 'INACTIVE')", TEMPLATE, ZONE);
        jdbc.update("insert into catalog_delivery_slot (id, template_id, zone_id, delivery_date, service_type, "
                + "start_at, end_at, cutoff_at, price_clp, capacity) values (?::uuid, ?::uuid, ?::uuid, "
                + "'2031-04-07', 'HOME_DELIVERY', '2031-04-07T16:00:00-04:00', '2031-04-07T18:00:00-04:00', "
                + "'2031-04-07T14:00:00-04:00', 2990, 5)", SLOT, TEMPLATE, ZONE);
    }

    @Test
    void createsEveryReservationTable() {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_name like 'reservation\\_%' order by table_name", String.class))
                .containsExactly("reservation_booking", "reservation_idempotency_key", "reservation_slot_hold");
    }

    @Test
    void declaresEveryColumnNotNullExceptTheClosingInstantsAndTheIdempotencyResult() {
        assertThat(jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
                + "where table_name like 'reservation\\_%' and is_nullable = 'YES'", String.class))
                .containsExactlyInAnyOrder("reservation_slot_hold.closed_at", "reservation_booking.cancelled_at",
                        "reservation_idempotency_key.outcome", "reservation_idempotency_key.response_payload");
    }

    @Test
    void allowsASingleActiveHoldPerCustomerAndOrder() {
        jdbc.update(INSERT_HOLD, "customer-1", "order-1", SLOT, "ACTIVE", null);

        assertRejected("reservation_slot_hold_active_order_uq",
                INSERT_HOLD, "customer-1", "order-1", SLOT, "ACTIVE", null);

        jdbc.update(INSERT_HOLD, "customer-1", "order-1", SLOT, "EXPIRED", "2031-01-01T00:00:00Z");
        jdbc.update(INSERT_HOLD, "customer-1", "order-2", SLOT, "ACTIVE", null);
        jdbc.update(INSERT_HOLD, "customer-2", "order-1", SLOT, "ACTIVE", null);
        assertThat(countHolds()).isEqualTo(4);
    }

    @Test
    void rejectsHoldsWithAnUnknownSlotStatusOrIncoherentClosingInstant() {
        assertRejected("reservation_slot_hold_slot_id_fkey",
                INSERT_HOLD, "customer-1", "order-1", UNKNOWN_SLOT, "ACTIVE", null);
        assertRejected("reservation_slot_hold_status_allowed",
                INSERT_HOLD, "customer-1", "order-1", SLOT, "PENDING", "2031-01-01T00:00:00Z");
        assertRejected("reservation_slot_hold_closed_iff_not_active",
                INSERT_HOLD, "customer-1", "order-1", SLOT, "ACTIVE", "2031-01-01T00:00:00Z");
        assertRejected("reservation_slot_hold_closed_iff_not_active",
                INSERT_HOLD, "customer-1", "order-1", SLOT, "RELEASED", null);
        assertThat(countHolds()).isZero();
    }

    @Test
    void rejectsHoldsThatDoNotExpireAfterTheirCreationOrHaveANegativePrice() {
        assertRejected("reservation_slot_hold_expires_after_creation",
                "insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                        + "start_at, end_at, cutoff_at, price_clp, status, expires_at) values (gen_random_uuid(), "
                        + "'customer-1', 'order-1', ?::uuid, ?::uuid, '2031-04-07', now(), now(), now(), 0, "
                        + "'ACTIVE', now())", SLOT, ZONE);
        assertRejected("reservation_slot_hold_price_non_negative",
                "insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, delivery_date, "
                        + "start_at, end_at, cutoff_at, price_clp, status, expires_at) values (gen_random_uuid(), "
                        + "'customer-1', 'order-1', ?::uuid, ?::uuid, '2031-04-07', now(), now(), now(), -1, "
                        + "'ACTIVE', now() + interval '1 minute')", SLOT, ZONE);
    }

    @Test
    void allowsASingleConfirmedReservationPerCustomerAndOrder() {
        String first = insertClosedHold("customer-1", "order-1");
        String second = insertClosedHold("customer-1", "order-1");
        jdbc.update(INSERT_BOOKING, first, "customer-1", "order-1", SLOT, "CONFIRMED", null);

        assertRejected("reservation_booking_confirmed_order_uq",
                INSERT_BOOKING, second, "customer-1", "order-1", SLOT, "CONFIRMED", null);

        jdbc.update(INSERT_BOOKING, second, "customer-1", "order-1", SLOT, "CANCELLED", "2031-01-01T00:00:00Z");
        assertThat(jdbc.queryForObject("select count(*) from reservation_booking where zone_id = ?::uuid",
                Integer.class, ZONE)).isEqualTo(2);
    }

    @Test
    void linksEachReservationToASingleExistingHold() {
        String hold = insertClosedHold("customer-1", "order-1");
        jdbc.update(INSERT_BOOKING, hold, "customer-1", "order-1", SLOT, "CONFIRMED", null);

        assertRejected("reservation_booking_hold_unique",
                INSERT_BOOKING, hold, "customer-1", "order-1", SLOT, "CANCELLED", "2031-01-01T00:00:00Z");
        assertRejected("reservation_booking_hold_id_fkey",
                INSERT_BOOKING, HOLD, "customer-1", "order-2", SLOT, "CONFIRMED", null);
    }

    @Test
    void rejectsReservationsWithAnUnknownSlotStatusOrIncoherentCancellation() {
        String hold = insertClosedHold("customer-1", "order-1");

        assertRejected("reservation_booking_slot_id_fkey",
                INSERT_BOOKING, hold, "customer-1", "order-1", UNKNOWN_SLOT, "CONFIRMED", null);
        assertRejected("reservation_booking_status_allowed",
                INSERT_BOOKING, hold, "customer-1", "order-1", SLOT, "PENDING", null);
        assertRejected("reservation_booking_cancelled_iff_cancelled_at",
                INSERT_BOOKING, hold, "customer-1", "order-1", SLOT, "CONFIRMED", "2031-01-01T00:00:00Z");
        assertRejected("reservation_booking_cancelled_iff_cancelled_at",
                INSERT_BOOKING, hold, "customer-1", "order-1", SLOT, "CANCELLED", null);
    }

    @Test
    void scopesIdempotencyKeysPerCustomer() {
        jdbc.update(INSERT_KEY, "customer-1", "key-1", "HOLD_SLOT", null);
        jdbc.update(INSERT_KEY, "customer-2", "key-1", "HOLD_SLOT", "SUCCEEDED");

        assertRejected("reservation_idempotency_key_pk", INSERT_KEY, "customer-1", "key-1", "CONFIRM_HOLD", null);
        assertThat(jdbc.queryForObject("select count(*) from reservation_idempotency_key "
                + "where idempotency_key = 'key-1' "
                + "and customer_id in ('customer-1', 'customer-2')", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsIdempotencyKeysWithAnUnknownOperationOrOutcome() {
        assertRejected("reservation_idempotency_key_operation_allowed",
                INSERT_KEY, "customer-1", "key-1", "CANCEL", null);
        assertRejected("reservation_idempotency_key_outcome_allowed",
                INSERT_KEY, "customer-1", "key-1", "HOLD_SLOT", "FAILED");
    }

    @Test
    void indexesActiveHoldsByExpiryAndKeysByExpiry() {
        assertThat(indexes("reservation_slot_hold"))
                .anySatisfy(definition -> assertThat(definition).contains("reservation_slot_hold_due_idx")
                        .contains("(expires_at)").contains("WHERE ((status)::text = 'ACTIVE'::text)"))
                .anySatisfy(definition -> assertThat(definition).contains("reservation_slot_hold_active_order_uq")
                        .contains("UNIQUE").contains("(customer_id, order_id)")
                        .contains("WHERE ((status)::text = 'ACTIVE'::text)"));
        assertThat(indexes("reservation_booking"))
                .anySatisfy(definition -> assertThat(definition).contains("reservation_booking_confirmed_order_uq")
                        .contains("UNIQUE").contains("WHERE ((status)::text = 'CONFIRMED'::text)"));
        assertThat(indexes("reservation_idempotency_key"))
                .anySatisfy(definition -> assertThat(definition)
                        .contains("reservation_idempotency_key_expires_idx").contains("(expires_at)"));
    }

    private String insertClosedHold(String customer, String order) {
        return jdbc.queryForObject("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, zone_id, "
                + "delivery_date, start_at, end_at, cutoff_at, price_clp, status, expires_at, closed_at) values "
                + "(gen_random_uuid(), ?, ?, ?::uuid, ?::uuid, '2031-04-07', '2031-04-07T16:00:00-04:00', "
                + "'2031-04-07T18:00:00-04:00', '2031-04-07T14:00:00-04:00', 2990, 'CONFIRMED', "
                + "now() + interval '10 minutes', now()) returning id::text",
                String.class, customer, order, SLOT, ZONE);
    }

    private int countHolds() {
        return jdbc.queryForObject("select count(*) from reservation_slot_hold where zone_id = ?::uuid", Integer.class,
                ZONE);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList("select indexdef from pg_indexes where tablename = ?", String.class, table);
    }

    /** Runs a statement expected to violate {@code constraint} inside a savepoint, so the test transaction survives. */
    private void assertRejected(String constraint, String sql, Object... args) {
        jdbc.execute("savepoint rejected_statement");
        try {
            assertThatThrownBy(() -> jdbc.update(sql, args))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining(constraint);
        } finally {
            jdbc.execute("rollback to savepoint rejected_statement");
        }
    }
}
