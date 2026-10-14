package cl.walmart.slot.reservation.infrastructure.outgoing.repository.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots;
import cl.walmart.slot.reservation.support.reservation.ReservationTestSlots.TestSlot;
import cl.walmart.slot.reservation.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReservationRepositoryImplIT {

    private static final Duration TTL = Duration.ofMinutes(15);

    @Autowired
    private ReservationRepository repository;
    @Autowired
    private HoldRepository holds;
    @Autowired
    private JdbcTemplate jdbc;

    private ReservationTestSlots slots;
    private TestSlot slot;
    private CustomerId customer;

    @BeforeEach
    void setUp() {
        slots = new ReservationTestSlots(jdbc);
        slot = slots.createSlot(50);
        customer = new CustomerId(slots.newCustomerId());
    }

    @AfterEach
    void tearDown() {
        slots.cleanup();
    }

    @Test
    void insertConfirmedCopiesTheSnapshotAndStampsTheDatabaseClock() {
        SlotHold hold = confirmedHold(customer, order());
        Instant before = slots.dbNow();

        Reservation reservation = repository.insertConfirmed(ReservationId.random(), hold).orElseThrow();

        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.holdId()).isEqualTo(hold.id());
        assertThat(reservation.customerId()).isEqualTo(customer);
        assertThat(reservation.orderId()).isEqualTo(hold.orderId());
        assertThat(reservation.slot()).isEqualTo(snapshot());
        assertThat(reservation.cancelledAt()).isNull();
        assertThat(reservation.confirmedAt()).isBetween(before.minusSeconds(1), slots.dbNow());
    }

    @Test
    void insertConfirmedDoesNothingWhenTheOrderAlreadyHasAConfirmedReservation() {
        OrderId order = order();
        Reservation first = repository.insertConfirmed(ReservationId.random(), confirmedHold(customer, order))
                .orElseThrow();

        SlotHold secondHold = confirmedHold(customer, order);
        assertThat(repository.insertConfirmed(ReservationId.random(), secondHold)).isEmpty();

        assertThat(repository.findConfirmedForOrder(customer, order)).contains(first);
    }

    @Test
    void findForCustomerReturnsTheReservationWithTheDatabaseNow() {
        Reservation reservation = repository.insertConfirmed(ReservationId.random(),
                confirmedHold(customer, order())).orElseThrow();
        Instant before = slots.dbNow();

        ReservationRecord record = repository.findForCustomer(reservation.id(), customer).orElseThrow();

        assertThat(record.reservation()).isEqualTo(reservation);
        assertThat(record.dbNow()).isBetween(before.minusSeconds(1), slots.dbNow());
    }

    @Test
    void aReservationOfAnotherCustomerIsInvisibleAndImmutable() {
        OrderId order = order();
        Reservation reservation = repository.insertConfirmed(ReservationId.random(),
                confirmedHold(customer, order)).orElseThrow();
        CustomerId other = new CustomerId(slots.newCustomerId());

        assertThat(repository.findForCustomer(reservation.id(), other)).isEmpty();
        assertThat(repository.findConfirmedForOrder(other, order)).isEmpty();
        assertThat(repository.markCancelled(reservation.id(), other)).isEmpty();
        assertThat(repository.findForCustomer(reservation.id(), customer).orElseThrow().reservation().status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void markCancelledTransitionsOnceAndStampsTheDatabaseClock() {
        Reservation reservation = repository.insertConfirmed(ReservationId.random(),
                confirmedHold(customer, order())).orElseThrow();
        Instant before = slots.dbNow();

        Reservation cancelled = repository.markCancelled(reservation.id(), customer).orElseThrow();

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isBetween(before.minusSeconds(1), slots.dbNow());
        assertThat(cancelled.id()).isEqualTo(reservation.id());
        assertThat(repository.markCancelled(reservation.id(), customer)).isEmpty();
        assertThat(repository.findConfirmedForOrder(customer, reservation.orderId())).isEmpty();
    }

    @Test
    void markCancelledIsRejectedOnceTheWindowClosedAccordingToTheDatabase() {
        Reservation reservation = repository.insertConfirmed(ReservationId.random(),
                confirmedHold(customer, order())).orElseThrow();
        jdbc.update("update reservation_booking set cutoff_at = now() - interval '1 minute' where id = ?",
                reservation.id().value());

        assertThat(repository.markCancelled(reservation.id(), customer)).isEmpty();
        assertThat(repository.findForCustomer(reservation.id(), customer).orElseThrow().reservation().status())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void aNewConfirmationIsAllowedOnceThePreviousReservationWasCancelled() {
        OrderId order = order();
        Reservation first = repository.insertConfirmed(ReservationId.random(), confirmedHold(customer, order))
                .orElseThrow();
        repository.markCancelled(first.id(), customer).orElseThrow();

        assertThat(repository.insertConfirmed(ReservationId.random(), confirmedHold(customer, order))).isPresent();
    }

    private SlotHold confirmedHold(CustomerId owner, OrderId order) {
        SlotHold active = holds.insertActive(HoldId.random(), owner, order, snapshot(), TTL).orElseThrow();
        return holds.markConfirmed(active.id(), owner).orElseThrow();
    }

    private OrderId order() {
        return new OrderId(slots.newOrderId());
    }

    private SlotSnapshot snapshot() {
        return new SlotSnapshot(new SlotId(slot.id()), slot.zoneId(), slot.deliveryDate(), slot.startAt(),
                slot.endAt(), slot.cutoffAt(), slot.priceClp());
    }
}
