package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
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
import cl.walmart.slot.reservation.domain.entities.reservation.ZoneDay;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class CancelReservationUseCaseImplTest {

    private static final Instant DB_NOW = Instant.parse("2031-03-01T12:00:00Z");
    private static final UUID ZONE = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2031, 3, 2);
    private static final CustomerId CUSTOMER = new CustomerId("cust-1");
    private static final OrderId ORDER = new OrderId("order-1");
    private static final Instant START = Instant.parse("2031-03-02T13:00:00Z");
    private static final SlotSnapshot SNAPSHOT = new SlotSnapshot(new SlotId(UUID.randomUUID()), ZONE, DAY, START,
            START.plusSeconds(7200), START.minusSeconds(86_400), 3990L);

    private final List<String> calls = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private final List<String> rejectedCodes = new ArrayList<>();
    private Optional<Reservation> markResult = Optional.empty();
    private Optional<ReservationRecord> found = Optional.empty();
    private SlotId releasedSlot;

    private final CancelReservationUseCaseImpl useCase = new CancelReservationUseCaseImpl(
            () -> CUSTOMER,
            new ReservationRepository() {
                @Override
                public Optional<Reservation> insertConfirmed(ReservationId id, SlotHold hold) {
                    throw new AssertionError();
                }

                @Override
                public Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer) {
                    calls.add("findForCustomer");
                    return found;
                }

                @Override
                public Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order) {
                    throw new AssertionError();
                }

                @Override
                public Optional<Reservation> markCancelled(ReservationId id, CustomerId customer) {
                    calls.add("markCancelled");
                    return markResult;
                }
            },
            new SlotCapacityService() {
                @Override
                public HoldCapacityOutcome tryHold(SlotId slot) {
                    throw new AssertionError();
                }

                @Override
                public ZoneDay moveHeldToReserved(SlotId slot) {
                    throw new AssertionError();
                }

                @Override
                public Set<ZoneDay> releaseHeld(Map<SlotId, Integer> counts) {
                    throw new AssertionError();
                }

                @Override
                public ZoneDay releaseReserved(SlotId slot) {
                    calls.add("releaseReserved");
                    releasedSlot = slot;
                    return new ZoneDay(ZONE, DAY);
                }
            },
            new ReservationMetrics() {
                @Override
                public void lifecycle(ReservationLifecycleEvent.Kind kind, int count) {
                    throw new AssertionError();
                }

                @Override
                public void holdRejected(String code) {
                    rejectedCodes.add(code);
                }

                @Override
                public void idempotency(String result) {
                    throw new AssertionError();
                }
            },
            published::add);

    private static Reservation reservation(ReservationStatus status, SlotSnapshot slot) {
        var cancelledAt = status == ReservationStatus.CANCELLED ? DB_NOW.minusSeconds(5) : null;
        return new Reservation(ReservationId.random(), HoldId.random(), CUSTOMER, ORDER, slot, status,
                DB_NOW.minusSeconds(600), cancelledAt);
    }

    @Test
    @DisplayName("es el límite transaccional y no revierte por rechazos de negocio")
    void declaresTransactionWithBusinessRejectionsNotRollingBack() {
        var tx = CancelReservationUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).containsExactly(ReservationException.class);
    }

    @Test
    @DisplayName("éxito: cancela con guarda, devuelve el cupo reservado, publica RESERVATION_CANCELLED y responde cancelada")
    void success() {
        var cancelled = reservation(ReservationStatus.CANCELLED, SNAPSHOT);
        markResult = Optional.of(cancelled);

        var view = useCase.cancel(cancelled.id());

        assertThat(calls).containsExactly("markCancelled", "releaseReserved");
        assertThat(releasedSlot).isEqualTo(SNAPSHOT.slotId());
        assertThat(view.reservationId()).isEqualTo(cancelled.id().value());
        assertThat(view.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(view.cancelledAt()).isEqualTo(cancelled.cancelledAt());
        assertThat(view.slot().slotId()).isEqualTo(SNAPSHOT.slotId().value());
        assertThat(published).containsExactly(new ReservationLifecycleEvent(
                ReservationLifecycleEvent.Kind.RESERVATION_CANCELLED, 1, Set.of(new ZoneDay(ZONE, DAY))));
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida y reserva ausente o ajena: RESERVATION_NOT_FOUND sin escrituras")
    void notFound() {
        var missing = ReservationId.random();

        assertThatThrownBy(() -> useCase.cancel(missing))
                .isInstanceOfSatisfying(ReservationException.class, e -> assertThat(e.reservationCode())
                        .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND));
        assertThat(calls).containsExactly("markCancelled", "findForCustomer");
        assertThat(rejectedCodes).containsExactly("RESERVATION_NOT_FOUND");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con reserva ya CANCELLED: misma vista, sin cambios ni eventos")
    void alreadyCancelledReturnsSameView() {
        var cancelled = reservation(ReservationStatus.CANCELLED, SNAPSHOT);
        found = Optional.of(new ReservationRecord(cancelled, DB_NOW));

        var view = useCase.cancel(cancelled.id());

        assertThat(view.reservationId()).isEqualTo(cancelled.id().value());
        assertThat(view.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(view.cancelledAt()).isEqualTo(cancelled.cancelledAt());
        assertThat(calls).containsExactly("markCancelled", "findForCustomer");
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con reserva CONFIRMED y cierre alcanzado: RESERVATION_CANCELLATION_CLOSED")
    void cutoffReachedIsClosed() {
        var closedSlot = new SlotSnapshot(SNAPSHOT.slotId(), ZONE, DAY, START, START.plusSeconds(7200),
                DB_NOW, 3990L);
        var confirmed = reservation(ReservationStatus.CONFIRMED, closedSlot);
        found = Optional.of(new ReservationRecord(confirmed, DB_NOW));

        assertThatThrownBy(() -> useCase.cancel(confirmed.id()))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.RESERVATION_CANCELLATION_CLOSED);
                    assertThat(e.details()).containsEntry("cutoffAt", DB_NOW.toString());
                });
        assertThat(rejectedCodes).containsExactly("RESERVATION_CANCELLATION_CLOSED");
        assertThat(calls).doesNotContain("releaseReserved");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida pero CONFIRMED antes del cierre: estado inconsistente, error interno")
    void confirmedBeforeCutoffAfterFailedGuardIsInternalError() {
        var confirmed = reservation(ReservationStatus.CONFIRMED, SNAPSHOT);
        found = Optional.of(new ReservationRecord(confirmed, DB_NOW));

        assertThatThrownBy(() -> useCase.cancel(confirmed.id())).isInstanceOf(IllegalStateException.class);
        assertThat(published).isEmpty();
    }
}
