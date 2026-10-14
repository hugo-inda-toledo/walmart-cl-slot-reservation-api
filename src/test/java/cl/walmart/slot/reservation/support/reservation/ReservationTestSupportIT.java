package cl.walmart.slot.reservation.support.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.reservation.ReservationInvariants.SlotCounters;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReservationTestSupportIT {

    private static final ZoneId SANTIAGO = ZoneId.of(ReservationTestSlots.TIME_ZONE);

    @Autowired
    private JdbcTemplate jdbc;

    private ReservationTestSlots fixture;
    private ReservationInvariants invariants;

    @BeforeEach
    void createFixture() {
        fixture = new ReservationTestSlots(jdbc);
        invariants = new ReservationInvariants(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixture.cleanup();
    }

    @Test
    void createsAnIsolatedSlotThatTheConservationCheckReportsConsistentAndCleansItUp() {
        TestSlot slot = fixture.createSlot(5);

        assertThat(invariants.countersOf(List.of(slot.id())))
                .containsExactly(new SlotCounters(slot.id(), 5, 0, 0, 0, 0));
        invariants.assertConserved(slot.id());

        String template = jdbc.queryForObject("select t.status from catalog_slot_template t "
                + "join catalog_delivery_slot s on s.template_id = t.id where s.id = ?", String.class, slot.id());
        assertThat(template).isEqualTo("INACTIVE");

        fixture.cleanup();

        assertThat(count("select count(*) from catalog_delivery_slot where id = ?", slot.id())).isZero();
        assertThat(count("select count(*) from catalog_slot_template where zone_id = ? and status = 'INACTIVE' "
                + "and end_time - start_time = interval '1 second'", ReservationTestSlots.ZONE_ID)).isZero();
    }

    @Test
    void placesSlotsInTheOwnDateBandOfASeededZoneWithInstantsRelativeToTheDatabase() {
        LocalDate today = jdbc.queryForObject("select (now() at time zone 'America/Santiago')::date",
                LocalDate.class);
        TestSlot first = fixture.createSlot(5);
        TestSlot later = fixture.slot().capacity(2).dayOffset(3).startingAt(LocalTime.of(16, 0), Duration.ofHours(2))
                .cutoffBeforeStart(Duration.ofHours(4)).priceClp(0).create();
        TestSlot closing = fixture.slot().cutoffFromDbNow(Duration.ofMinutes(-1)).create();
        Instant dbNow = fixture.dbNow();

        assertThat(first.zoneId()).isEqualTo(ReservationTestSlots.ZONE_ID);
        assertThat(first.deliveryDate()).isEqualTo(today.plusDays(ReservationTestSlots.BAND_START_DAYS));
        assertThat(first.startAt()).isEqualTo(first.deliveryDate().atTime(10, 0).atZone(SANTIAGO).toInstant());
        assertThat(first.endAt()).isEqualTo(first.startAt().plus(Duration.ofHours(2)));
        assertThat(first.cutoffAt()).isEqualTo(first.startAt().minus(Duration.ofHours(2)));
        assertThat(first.priceClp()).isEqualTo(2990);

        assertThat(later.deliveryDate()).isEqualTo(first.deliveryDate().plusDays(3));
        assertThat(later.startAt()).isEqualTo(later.deliveryDate().atTime(16, 0).atZone(SANTIAGO).toInstant());
        assertThat(later.cutoffAt()).isEqualTo(later.startAt().minus(Duration.ofHours(4)));
        assertThat(later.capacity()).isEqualTo(2);
        assertThat(later.priceClp()).isZero();

        assertThat(closing.deliveryDate()).isEqualTo(first.deliveryDate());
        assertThat(closing.cutoffAt()).isBefore(dbNow).isAfter(dbNow.minus(Duration.ofMinutes(2)));
        assertThat(fixture.slotIds()).containsExactly(first.id(), later.id(), closing.id());
    }

    @Test
    void reportsCountersThatDoNotMatchTheirHoldsAndReservations() {
        TestSlot slot = fixture.createSlot(5);
        String customer = fixture.newCustomerId();
        insertHold(slot, customer, fixture.newOrderId(), "ACTIVE");
        jdbc.update("update catalog_delivery_slot set held = 2 where id = ?", slot.id());

        assertThat(invariants.violationsOf(List.of(slot.id())))
                .containsExactly(new SlotCounters(slot.id(), 5, 2, 0, 1, 0));

        jdbc.update("update catalog_delivery_slot set held = 1 where id = ?", slot.id());
        invariants.assertConserved(slot.id());
    }

    @Test
    void removesKeysReservationsHoldsSlotsTemplatesAndBlocksCreatedThroughTheFixture() {
        TestSlot slot = fixture.createSlot(5);
        LocalDate blocked = fixture.blockZoneOn(5);
        String customer = fixture.newCustomerId();
        UUID hold = insertHold(slot, customer, fixture.newOrderId(), "CONFIRMED");
        jdbc.update("insert into reservation_booking (id, hold_id, customer_id, order_id, slot_id, zone_id, "
                + "delivery_date, start_at, end_at, cutoff_at, price_clp, status, confirmed_at) select "
                + "gen_random_uuid(), id, customer_id, order_id, slot_id, zone_id, delivery_date, start_at, end_at, "
                + "cutoff_at, price_clp, 'CONFIRMED', now() from reservation_slot_hold where id = ?", hold);
        jdbc.update("insert into reservation_idempotency_key (customer_id, idempotency_key, operation, "
                + "request_fingerprint, expires_at) values (?, 'key-1', 'HOLD_SLOT', repeat('a', 64), "
                + "now() + interval '1 day')", customer);
        assertThat(count("select count(*) from catalog_holiday where holiday_date = ? and zone_id = ?",
                blocked, ReservationTestSlots.ZONE_ID)).isEqualTo(1);

        fixture.cleanup();

        assertThat(count("select count(*) from reservation_idempotency_key where customer_id = ?", customer))
                .isZero();
        assertThat(count("select count(*) from reservation_booking where customer_id = ?", customer)).isZero();
        assertThat(count("select count(*) from reservation_slot_hold where customer_id = ?", customer)).isZero();
        assertThat(count("select count(*) from catalog_delivery_slot where id = ?", slot.id())).isZero();
        assertThat(count("select count(*) from catalog_holiday where holiday_date = ? and zone_id = ?",
                blocked, ReservationTestSlots.ZONE_ID)).isZero();
        // Band of the fixture only: catalog ITs keep their own fixed dates (2030–2037), far beyond it.
        assertThat(count("select count(*) from catalog_delivery_slot where zone_id = ? "
                        + "and delivery_date between ? and ?", ReservationTestSlots.ZONE_ID,
                fixture.bandDate(0), fixture.bandDate(30))).isZero();
    }

    private UUID insertHold(TestSlot slot, String customer, String order, String status) {
        return jdbc.queryForObject("insert into reservation_slot_hold (id, customer_id, order_id, slot_id, "
                + "zone_id, delivery_date, start_at, end_at, cutoff_at, price_clp, status, expires_at, closed_at) "
                + "select gen_random_uuid(), ?, ?, id, zone_id, delivery_date, start_at, end_at, cutoff_at, "
                + "price_clp, ?, now() + interval '10 minutes', case when ? = 'ACTIVE' then null else now() end "
                + "from catalog_delivery_slot where id = ? returning id", UUID.class, customer, order, status, status,
                slot.id());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
