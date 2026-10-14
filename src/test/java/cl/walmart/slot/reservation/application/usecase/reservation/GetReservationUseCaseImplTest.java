package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.ReservationRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class GetReservationUseCaseImplTest {

    private static final Instant DB_NOW = Instant.parse("2031-03-01T12:00:00Z");
    private static final CustomerId CUSTOMER = new CustomerId("cust-1");
    private static final OrderId ORDER = new OrderId("order-1");
    private static final Instant START = Instant.parse("2031-03-02T13:00:00Z");
    private static final SlotSnapshot SNAPSHOT = new SlotSnapshot(new SlotId(UUID.randomUUID()), UUID.randomUUID(),
            LocalDate.of(2031, 3, 2), START, START.plusSeconds(7200), START.minusSeconds(86_400), 3990L);

    private final List<CustomerId> askedBy = new ArrayList<>();
    private Optional<ReservationRecord> found = Optional.empty();

    private final GetReservationUseCaseImpl useCase = new GetReservationUseCaseImpl(() -> CUSTOMER,
            new ReservationRepository() {
                @Override
                public Optional<Reservation> insertConfirmed(ReservationId id, SlotHold confirmedHold) {
                    throw new AssertionError();
                }

                @Override
                public Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer) {
                    askedBy.add(customer);
                    return found;
                }

                @Override
                public Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order) {
                    throw new AssertionError();
                }

                @Override
                public Optional<Reservation> markCancelled(ReservationId id, CustomerId customer) {
                    throw new AssertionError();
                }
            });

    private static Reservation reservation(ReservationStatus status) {
        var cancelledAt = status == ReservationStatus.CANCELLED ? DB_NOW.minusSeconds(5) : null;
        return new Reservation(ReservationId.random(), HoldId.random(), CUSTOMER, ORDER, SNAPSHOT, status,
                DB_NOW.minusSeconds(600), cancelledAt);
    }

    @Test
    @DisplayName("es de solo lectura")
    void isReadOnlyTransaction() {
        var tx = GetReservationUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

    @Test
    @DisplayName("reserva confirmada: identificador, retención de origen, orden, estado, confirmación y ventana")
    void confirmed() {
        var confirmed = reservation(ReservationStatus.CONFIRMED);
        found = Optional.of(new ReservationRecord(confirmed, DB_NOW));

        var view = useCase.get(confirmed.id());

        assertThat(askedBy).containsExactly(CUSTOMER);
        assertThat(view.reservationId()).isEqualTo(confirmed.id().value());
        assertThat(view.holdId()).isEqualTo(confirmed.holdId().value());
        assertThat(view.orderId()).isEqualTo("order-1");
        assertThat(view.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(view.confirmedAt()).isEqualTo(confirmed.confirmedAt());
        assertThat(view.cancelledAt()).isNull();
        assertThat(view.slot()).isEqualTo(new SlotSnapshotView(SNAPSHOT.slotId().value(), SNAPSHOT.zoneId(),
                SNAPSHOT.deliveryDate(), START, START.plusSeconds(7200), 3990L));
    }

    @Test
    @DisplayName("reserva cancelada: informa el instante de cancelación")
    void cancelled() {
        var cancelled = reservation(ReservationStatus.CANCELLED);
        found = Optional.of(new ReservationRecord(cancelled, DB_NOW));

        var view = useCase.get(cancelled.id());

        assertThat(view.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(view.cancelledAt()).isEqualTo(cancelled.cancelledAt());
    }

    @Test
    @DisplayName("ausente o ajena: RESERVATION_NOT_FOUND")
    void notFound() {
        var missing = ReservationId.random();

        assertThatThrownBy(() -> useCase.get(missing))
                .isInstanceOfSatisfying(ReservationException.class, e -> assertThat(e.reservationCode())
                        .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND));
        assertThat(askedBy).containsExactly(CUSTOMER);
    }
}
