package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotHold;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class GetHoldUseCaseImplTest {

    private static final Instant DB_NOW = Instant.parse("2031-03-01T12:00:00Z");
    private static final CustomerId CUSTOMER = new CustomerId("cust-1");
    private static final OrderId ORDER = new OrderId("order-1");
    private static final Instant START = Instant.parse("2031-03-02T13:00:00Z");
    private static final SlotSnapshot SNAPSHOT = new SlotSnapshot(new SlotId(UUID.randomUUID()), UUID.randomUUID(),
            LocalDate.of(2031, 3, 2), START, START.plusSeconds(7200), START.minusSeconds(86_400), 3990L);

    private final List<CustomerId> askedBy = new ArrayList<>();
    private Optional<HoldRecord> found = Optional.empty();

    private final GetHoldUseCaseImpl useCase = new GetHoldUseCaseImpl(() -> CUSTOMER, new HoldRepository() {
        @Override
        public Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order, SlotSnapshot slot,
                                               Duration ttl) {
            throw new AssertionError();
        }

        @Override
        public Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer) {
            askedBy.add(customer);
            return found;
        }

        @Override
        public Optional<HoldRecord> findActiveForOrder(CustomerId customer, OrderId order) {
            throw new AssertionError();
        }

        @Override
        public Optional<SlotHold> expireStaleForOrder(CustomerId customer, OrderId order) {
            throw new AssertionError();
        }

        @Override
        public Optional<SlotHold> markConfirmed(HoldId id, CustomerId customer) {
            throw new AssertionError();
        }

        @Override
        public Optional<SlotHold> markReleased(HoldId id, CustomerId customer) {
            throw new AssertionError();
        }

        @Override
        public List<SlotHold> expireDueBatch(int limit) {
            throw new AssertionError();
        }
    });

    private static SlotHold hold(HoldStatus status, Instant expiresAt) {
        var closedAt = status == HoldStatus.ACTIVE ? null : DB_NOW.minusSeconds(30);
        return new SlotHold(HoldId.random(), CUSTOMER, ORDER, SNAPSHOT, status, expiresAt.minusSeconds(600),
                expiresAt, closedAt);
    }

    @Test
    @DisplayName("es de solo lectura")
    void isReadOnlyTransaction() {
        var tx = GetHoldUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

    @Test
    @DisplayName("retención activa vigente: estado ACTIVE, ventana y vencimiento, sin reserva")
    void activeHold() {
        var active = hold(HoldStatus.ACTIVE, DB_NOW.plusSeconds(300));
        found = Optional.of(new HoldRecord(active, Optional.empty(), DB_NOW));

        var view = useCase.get(active.id());

        assertThat(askedBy).containsExactly(CUSTOMER);
        assertThat(view.holdId()).isEqualTo(active.id().value());
        assertThat(view.orderId()).isEqualTo("order-1");
        assertThat(view.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(view.createdAt()).isEqualTo(active.createdAt());
        assertThat(view.expiresAt()).isEqualTo(active.expiresAt());
        assertThat(view.reservationId()).isNull();
        assertThat(view.slot()).isEqualTo(new SlotSnapshotView(SNAPSHOT.slotId().value(), SNAPSHOT.zoneId(),
                SNAPSHOT.deliveryDate(), START, START.plusSeconds(7200), 3990L));
    }

    @Test
    @DisplayName("ACTIVE con vencimiento alcanzado según la hora de la base: se informa EXPIRED")
    void staleActiveIsReportedExpired() {
        var stale = hold(HoldStatus.ACTIVE, DB_NOW.minusSeconds(1));
        found = Optional.of(new HoldRecord(stale, Optional.empty(), DB_NOW));

        assertThat(useCase.get(stale.id()).status()).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    @DisplayName("ACTIVE que vence exactamente en la hora de la base: se informa EXPIRED")
    void activeExpiringExactlyNowIsExpired() {
        var edge = hold(HoldStatus.ACTIVE, DB_NOW);
        found = Optional.of(new HoldRecord(edge, Optional.empty(), DB_NOW));

        assertThat(useCase.get(edge.id()).status()).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    @DisplayName("retención CONFIRMED: informa la reserva resultante")
    void confirmedHoldCarriesReservationId() {
        var confirmed = hold(HoldStatus.CONFIRMED, DB_NOW.plusSeconds(300));
        var reservationId = ReservationId.random();
        found = Optional.of(new HoldRecord(confirmed, Optional.of(reservationId), DB_NOW));

        var view = useCase.get(confirmed.id());

        assertThat(view.status()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(view.reservationId()).isEqualTo(reservationId.value());
    }

    @Test
    @DisplayName("RELEASED y EXPIRED persistidos se informan tal cual")
    void closedStatusesAreReportedAsStored() {
        var released = hold(HoldStatus.RELEASED, DB_NOW.plusSeconds(300));
        var expired = hold(HoldStatus.EXPIRED, DB_NOW.minusSeconds(60));

        found = Optional.of(new HoldRecord(released, Optional.empty(), DB_NOW));
        assertThat(useCase.get(released.id()).status()).isEqualTo(HoldStatus.RELEASED);
        found = Optional.of(new HoldRecord(expired, Optional.empty(), DB_NOW));
        assertThat(useCase.get(expired.id()).status()).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    @DisplayName("ausente o ajena: HOLD_NOT_FOUND")
    void notFound() {
        var missing = HoldId.random();

        assertThatThrownBy(() -> useCase.get(missing))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_NOT_FOUND));
        assertThat(askedBy).containsExactly(CUSTOMER);
    }
}
