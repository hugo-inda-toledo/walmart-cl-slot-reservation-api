package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ConfirmHoldCommand;
import cl.walmart.slot.reservation.domain.service.reservation.IdempotencyService;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldCapacityOutcome;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldRecord;
import cl.walmart.slot.reservation.domain.repository.reservation.HoldRepository;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.Reservation;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationConflictException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ConfirmHoldUseCaseImplTest {

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
    private Optional<SlotHold> markResult = Optional.empty();
    private Optional<HoldRecord> found = Optional.empty();
    private boolean insertConflict;
    private Reservation inserted;
    private SlotId movedSlot;

    private final ConfirmHoldUseCaseImpl useCase = new ConfirmHoldUseCaseImpl(
            () -> CUSTOMER,
            new PassThroughIdempotency(),
            new HoldRepository() {
                @Override
                public Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order,
                                                       SlotSnapshot slot, Duration ttl) {
                    throw new AssertionError();
                }

                @Override
                public Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer) {
                    calls.add("findForCustomer");
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
                    calls.add("markConfirmed");
                    return markResult;
                }

                @Override
                public Optional<SlotHold> markReleased(HoldId id, CustomerId customer) {
                    throw new AssertionError();
                }

                @Override
                public List<SlotHold> expireDueBatch(int limit) {
                    throw new AssertionError();
                }
            },
            new ReservationRepository() {
                @Override
                public Optional<Reservation> insertConfirmed(ReservationId id, SlotHold hold) {
                    calls.add("insertConfirmed");
                    if (insertConflict) {
                        return Optional.empty();
                    }
                    inserted = new Reservation(id, hold.id(), hold.customerId(), hold.orderId(), hold.slot(),
                            ReservationStatus.CONFIRMED, DB_NOW, null);
                    return Optional.of(inserted);
                }

                @Override
                public Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer) {
                    throw new AssertionError();
                }

                @Override
                public Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order) {
                    throw new AssertionError();
                }

                @Override
                public Optional<Reservation> markCancelled(ReservationId id, CustomerId customer) {
                    throw new AssertionError();
                }
            },
            new SlotCapacityService() {
                @Override
                public HoldCapacityOutcome tryHold(SlotId slot) {
                    throw new AssertionError();
                }

                @Override
                public ZoneDay moveHeldToReserved(SlotId slot) {
                    calls.add("moveHeldToReserved");
                    movedSlot = slot;
                    return new ZoneDay(ZONE, DAY);
                }

                @Override
                public Set<ZoneDay> releaseHeld(Map<SlotId, Integer> counts) {
                    throw new AssertionError();
                }

                @Override
                public ZoneDay releaseReserved(SlotId slot) {
                    throw new AssertionError();
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

    private static SlotHold hold(HoldStatus status, Instant expiresAt) {
        var closedAt = status == HoldStatus.ACTIVE ? null : DB_NOW.minusSeconds(30);
        return new SlotHold(HoldId.random(), CUSTOMER, ORDER, SNAPSHOT, status, expiresAt.minusSeconds(600),
                expiresAt, closedAt);
    }

    private static ConfirmHoldCommand command(SlotHold hold) {
        return new ConfirmHoldCommand(hold.id(), new IdempotencyKey("key-1"));
    }

    private void assertRejection(ConfirmHoldCommand command, ReservationErrorCode code) {
        assertThatThrownBy(() -> useCase.confirm(command))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(code));
        assertThat(rejectedCodes).containsExactly(code.name());
        assertThat(calls).doesNotContain("insertConfirmed", "moveHeldToReserved");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("es el límite transaccional y no revierte por rechazos de negocio")
    void declaresTransactionWithBusinessRejectionsNotRollingBack() {
        var tx = ConfirmHoldUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).containsExactly(ReservationException.class);
    }

    @Test
    @DisplayName("éxito: confirma con guarda, inserta la reserva, traslada el cupo y publica HOLD_CONFIRMED una vez")
    void success() {
        var active = hold(HoldStatus.ACTIVE, DB_NOW.plusSeconds(300));
        var confirmedHold = new SlotHold(active.id(), CUSTOMER, ORDER, SNAPSHOT, HoldStatus.CONFIRMED,
                active.createdAt(), active.expiresAt(), DB_NOW);
        markResult = Optional.of(confirmedHold);

        var view = useCase.confirm(command(active));

        assertThat(calls).containsExactly("markConfirmed", "insertConfirmed", "moveHeldToReserved");
        assertThat(movedSlot).isEqualTo(SNAPSHOT.slotId());
        assertThat(view.reservationId()).isEqualTo(inserted.id().value());
        assertThat(view.holdId()).isEqualTo(active.id().value());
        assertThat(view.orderId()).isEqualTo("order-1");
        assertThat(view.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(view.confirmedAt()).isEqualTo(DB_NOW);
        assertThat(view.cancelledAt()).isNull();
        assertThat(view.slot().slotId()).isEqualTo(SNAPSHOT.slotId().value());
        assertThat(view.slot().priceClp()).isEqualTo(3990L);
        assertThat(published).containsExactly(new ReservationLifecycleEvent(
                ReservationLifecycleEvent.Kind.HOLD_CONFIRMED, 1, Set.of(new ZoneDay(ZONE, DAY))));
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida y retención ausente o ajena: HOLD_NOT_FOUND sin escrituras")
    void notFound() {
        var missing = new ConfirmHoldCommand(HoldId.random(), new IdempotencyKey("k"));

        assertRejection(missing, ReservationErrorCode.HOLD_NOT_FOUND);
        assertThat(calls).containsExactly("markConfirmed", "findForCustomer");
    }

    @Test
    @DisplayName("guarda fallida con ACTIVE vencida no barrida: HOLD_EXPIRED con su vencimiento")
    void activeButDueIsExpired() {
        var stale = hold(HoldStatus.ACTIVE, DB_NOW.minusSeconds(1));
        found = Optional.of(new HoldRecord(stale, Optional.empty(), DB_NOW));

        assertThatThrownBy(() -> useCase.confirm(command(stale)))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_EXPIRED);
                    assertThat(e.details()).containsEntry("expiresAt", stale.expiresAt().toString());
                });
        assertThat(rejectedCodes).containsExactly("HOLD_EXPIRED");
        assertThat(calls).doesNotContain("insertConfirmed", "moveHeldToReserved");
    }

    @Test
    @DisplayName("guarda fallida con retención ya EXPIRED: HOLD_EXPIRED")
    void alreadyExpired() {
        var expired = hold(HoldStatus.EXPIRED, DB_NOW.minusSeconds(60));
        found = Optional.of(new HoldRecord(expired, Optional.empty(), DB_NOW));

        assertRejection(command(expired), ReservationErrorCode.HOLD_EXPIRED);
    }

    @Test
    @DisplayName("guarda fallida con retención CONFIRMED: HOLD_NOT_ACTIVE con holdStatus y reservationId")
    void confirmedIsNotActiveWithReservation() {
        var confirmedHold = hold(HoldStatus.CONFIRMED, DB_NOW.plusSeconds(300));
        var reservationId = ReservationId.random();
        found = Optional.of(new HoldRecord(confirmedHold, Optional.of(reservationId), DB_NOW));

        assertThatThrownBy(() -> useCase.confirm(command(confirmedHold)))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_NOT_ACTIVE);
                    assertThat(e.details()).containsEntry("holdStatus", "CONFIRMED")
                            .containsEntry("reservationId", reservationId.value().toString());
                });
        assertThat(rejectedCodes).containsExactly("HOLD_NOT_ACTIVE");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con retención RELEASED: HOLD_NOT_ACTIVE sin reservationId")
    void releasedIsNotActiveWithoutReservation() {
        var released = hold(HoldStatus.RELEASED, DB_NOW.plusSeconds(300));
        found = Optional.of(new HoldRecord(released, Optional.empty(), DB_NOW));

        assertThatThrownBy(() -> useCase.confirm(command(released)))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_NOT_ACTIVE);
                    assertThat(e.details()).containsEntry("holdStatus", "RELEASED")
                            .doesNotContainKey("reservationId");
                });
        assertThat(calls).doesNotContain("insertConfirmed", "moveHeldToReserved");
    }

    @Test
    @DisplayName("la orden ya tiene una reserva confirmada: conflicto por carrera, sin traslado ni evento")
    void insertConflictIsARaceThatRollsBack() {
        var active = hold(HoldStatus.ACTIVE, DB_NOW.plusSeconds(300));
        markResult = Optional.of(new SlotHold(active.id(), CUSTOMER, ORDER, SNAPSHOT, HoldStatus.CONFIRMED,
                active.createdAt(), active.expiresAt(), DB_NOW));
        insertConflict = true;

        assertThatThrownBy(() -> useCase.confirm(command(active)))
                .isInstanceOfSatisfying(ReservationConflictException.class, e -> assertThat(e.reservationCode())
                        .isEqualTo(ReservationErrorCode.ORDER_ALREADY_RESERVED));
        assertThat(calls).containsExactly("markConfirmed", "insertConfirmed");
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).containsExactly("ORDER_ALREADY_RESERVED");
        assertThat(ReservationConflictException.class.getSuperclass()).isNotEqualTo(ReservationException.class);
    }

    /** Ejecuta la acción tal cual: la idempotencia tiene sus propias pruebas. */
    private static final class PassThroughIdempotency implements IdempotencyService {
        @Override
        public <T> T execute(IdempotentRequest request, Class<T> resultType, Supplier<T> action) {
            return action.get();
        }
    }
}
