package cl.walmart.slot.reservation.application.usecase.reservation;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import cl.walmart.slot.reservation.domain.entities.reservation.ExpiredBatch;
import cl.walmart.slot.reservation.domain.service.reservation.HoldService;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldSlotCommand;
import cl.walmart.slot.reservation.domain.service.reservation.IdempotencyService;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;
import cl.walmart.slot.reservation.domain.service.reservation.SlotCapacityService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.port.catalog.SlotQueryPort;
import cl.walmart.slot.reservation.domain.port.catalog.SlotView;
import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.port.reservation.ReservationMetrics;
import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
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

class HoldSlotUseCaseImplTest {

    private static final Instant DB_NOW = Instant.parse("2031-03-01T12:00:00Z");
    private static final UUID ZONE = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2031, 3, 2);
    private static final CustomerId CUSTOMER = new CustomerId("cust-1");
    private static final OrderId ORDER = new OrderId("order-1");

    private final List<String> calls = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private final List<String> rejectedCodes = new ArrayList<>();
    private final Map<UUID, SlotView> slots = new java.util.HashMap<>();
    private Optional<Reservation> confirmed = Optional.empty();
    private Optional<HoldRecord> active = Optional.empty();
    private Optional<ZoneDay> staleExpiry = Optional.empty();
    private HoldCapacityOutcome outcome;
    private boolean insertConflict;
    private List<AlternativeSlot> suggested = List.of();
    private SlotHold inserted;

    private final HoldSlotUseCaseImpl useCase = new HoldSlotUseCaseImpl(
            () -> CUSTOMER,
            new PassThroughIdempotency(),
            new SlotQueryPort() {
                @Override
                public Optional<SlotView> findSlot(UUID slotId) {
                    calls.add("findSlot");
                    return Optional.ofNullable(slots.get(slotId));
                }

                @Override
                public List<SlotView> findAvailableSlots(UUID zoneId, LocalDate from, LocalDate to) {
                    throw new AssertionError("el caso de uso no consulta disponibilidad: usa el finder");
                }
            },
            new ReservationRepository() {
                @Override
                public Optional<Reservation> insertConfirmed(ReservationId id, SlotHold hold) {
                    throw new AssertionError();
                }

                @Override
                public Optional<ReservationRecord> findForCustomer(ReservationId id, CustomerId customer) {
                    throw new AssertionError();
                }

                @Override
                public Optional<Reservation> findConfirmedForOrder(CustomerId customer, OrderId order) {
                    calls.add("findConfirmedForOrder");
                    return confirmed;
                }

                @Override
                public Optional<Reservation> markCancelled(ReservationId id, CustomerId customer) {
                    throw new AssertionError();
                }
            },
            new HoldRepository() {
                @Override
                public Optional<SlotHold> insertActive(HoldId id, CustomerId customer, OrderId order,
                                                       SlotSnapshot slot, Duration ttl) {
                    calls.add("insertActive");
                    if (insertConflict) {
                        return Optional.empty();
                    }
                    inserted = new SlotHold(id, customer, order, slot, HoldStatus.ACTIVE, DB_NOW,
                            DB_NOW.plus(ttl), null);
                    return Optional.of(inserted);
                }

                @Override
                public Optional<HoldRecord> findForCustomer(HoldId id, CustomerId customer) {
                    throw new AssertionError();
                }

                @Override
                public Optional<HoldRecord> findActiveForOrder(CustomerId customer, OrderId order) {
                    calls.add("findActiveForOrder");
                    return active;
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
            },
            new HoldService() {
                @Override
                public Optional<ZoneDay> expireStaleForOrder(CustomerId customer, OrderId order) {
                    calls.add("expireStaleForOrder");
                    return staleExpiry;
                }

                @Override
                public ExpiredBatch expireDue(int limit) {
                    throw new AssertionError();
                }
            },
            new SlotCapacityService() {
                @Override
                public HoldCapacityOutcome tryHold(SlotId slot) {
                    calls.add("tryHold");
                    return outcome;
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
                    throw new AssertionError();
                }
            },
            (requested, dbNow) -> {
                calls.add("alternatives@" + dbNow);
                return suggested;
            },
            new ReservationSettings(Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(2), 1,
                    100, 10, 1000),
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

    private HoldSlotCommand command(SlotView slot) {
        return new HoldSlotCommand(new SlotId(slot.slotId()), ORDER, new IdempotencyKey("key-1"));
    }

    private SlotView slot(SlotView.Status status) {
        var id = UUID.randomUUID();
        var start = Instant.parse("2031-03-02T13:00:00Z");
        var view = new SlotView(id, ZONE, DAY, start, start.plusSeconds(7200), start.minusSeconds(86_400), 3990L,
                10, 4, 1, 5, status);
        slots.put(id, view);
        return view;
    }

    private static SlotHold activeHold(Instant expiresAt) {
        var start = Instant.parse("2031-03-02T13:00:00Z");
        var snapshot = new SlotSnapshot(new SlotId(UUID.randomUUID()), ZONE, DAY, start, start.plusSeconds(7200),
                start.minusSeconds(86_400), 3990L);
        return new SlotHold(HoldId.random(), CUSTOMER, ORDER, snapshot, HoldStatus.ACTIVE,
                expiresAt.minusSeconds(600), expiresAt, null);
    }

    private void assertNoCapacityTaken() {
        assertThat(calls).doesNotContain("tryHold", "insertActive");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("es el límite transaccional y no revierte por rechazos de negocio")
    void declaresTransactionWithBusinessRejectionsNotRollingBack() {
        var tx = HoldSlotUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).containsExactly(ReservationException.class);
    }

    @Test
    @DisplayName("ventana inexistente: SLOT_NOT_FOUND sin tomar cupo")
    void slotNotFound() {
        var missing = new HoldSlotCommand(new SlotId(UUID.randomUUID()), ORDER, new IdempotencyKey("k"));

        assertRejection(missing, ReservationErrorCode.SLOT_NOT_FOUND);
        assertNoCapacityTaken();
        assertThat(calls).containsExactly("findSlot");
    }

    @Test
    @DisplayName("día sin servicio: SLOT_NO_SERVICE sin tomar cupo")
    void slotNoService() {
        var slot = slot(SlotView.Status.NO_SERVICE);

        assertRejection(command(slot), ReservationErrorCode.SLOT_NO_SERVICE);
        assertNoCapacityTaken();
    }

    @Test
    @DisplayName("orden ya reservada: ORDER_ALREADY_RESERVED antes de mirar retenciones y sin tomar cupo")
    void orderAlreadyReserved() {
        var slot = slot(SlotView.Status.AVAILABLE);
        confirmed = Optional.of(new Reservation(new ReservationId(UUID.randomUUID()), HoldId.random(), CUSTOMER,
                ORDER, activeHold(DB_NOW.plusSeconds(60)).slot(), ReservationStatus.CONFIRMED, DB_NOW, null));

        assertRejection(command(slot), ReservationErrorCode.ORDER_ALREADY_RESERVED);
        assertNoCapacityTaken();
        assertThat(calls).doesNotContain("findActiveForOrder", "expireStaleForOrder");
    }

    @Test
    @DisplayName("retención activa vigente: ACTIVE_HOLD_EXISTS con su identificador y sin tomar cupo")
    void activeHoldExists() {
        var slot = slot(SlotView.Status.AVAILABLE);
        var current = activeHold(DB_NOW.plusSeconds(300));
        active = Optional.of(new HoldRecord(current, Optional.empty(), DB_NOW));

        assertThatThrownBy(() -> useCase.hold(command(slot)))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.ACTIVE_HOLD_EXISTS);
                    assertThat(e.details()).containsEntry("holdId", current.id().value().toString());
                });
        assertNoCapacityTaken();
        assertThat(rejectedCodes).containsExactly("ACTIVE_HOLD_EXISTS");
    }

    @Test
    @DisplayName("retención vieja vencida: se expira antes de tomar cupo y la nueva retención se crea")
    void staleHoldIsExpiredBeforeContinuing() {
        var slot = slot(SlotView.Status.AVAILABLE);
        active = Optional.of(new HoldRecord(activeHold(DB_NOW), Optional.empty(), DB_NOW));
        staleExpiry = Optional.of(new ZoneDay(ZONE, DAY));
        outcome = new HoldCapacityOutcome.Acquired(new ZoneDay(ZONE, DAY));

        var view = useCase.hold(command(slot));

        assertThat(calls).containsSubsequence("findActiveForOrder", "expireStaleForOrder", "tryHold",
                "insertActive");
        assertThat(view.status()).isEqualTo(HoldStatus.ACTIVE);
    }

    @Test
    @DisplayName("agotada: SLOT_FULL con alternativas calculadas con la hora de la base y sin insertar")
    void soldOutRejectsWithAlternatives() {
        var slot = slot(SlotView.Status.SOLD_OUT);
        outcome = new HoldCapacityOutcome.SoldOut(DB_NOW);
        suggested = List.of(new AlternativeSlot(new SlotId(UUID.randomUUID()), DAY, DB_NOW.plusSeconds(10_000),
                DB_NOW.plusSeconds(17_200), 2990L, 2));

        assertThatThrownBy(() -> useCase.hold(command(slot)))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.SLOT_FULL);
                    assertThat(e.details()).containsKey("alternatives");
                    assertThat((List<?>) e.details().get("alternatives")).hasSize(1);
                });
        assertThat(calls).contains("alternatives@" + DB_NOW).doesNotContain("insertActive");
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).containsExactly("SLOT_FULL");
    }

    @Test
    @DisplayName("cerrada según la base: SLOT_CUTOFF_PASSED sin insertar ni evento")
    void cutoffPassed() {
        var slot = slot(SlotView.Status.AVAILABLE);
        outcome = new HoldCapacityOutcome.CutoffPassed(DB_NOW.minusSeconds(5));

        assertRejection(command(slot), ReservationErrorCode.SLOT_CUTOFF_PASSED);
        assertThat(calls).doesNotContain("insertActive");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("la ventana desaparece entre la lectura y la toma de cupo: SLOT_NOT_FOUND")
    void notFoundAtCapacity() {
        var slot = slot(SlotView.Status.AVAILABLE);
        outcome = new HoldCapacityOutcome.NotFound();

        assertRejection(command(slot), ReservationErrorCode.SLOT_NOT_FOUND);
        assertThat(calls).doesNotContain("insertActive");
    }

    @Test
    @DisplayName("éxito: inserta con el TTL, publica HOLD_CREATED una vez y devuelve la vista con snapshot")
    void successInsertsPublishesAndReturnsView() {
        var slot = slot(SlotView.Status.AVAILABLE);
        outcome = new HoldCapacityOutcome.Acquired(new ZoneDay(ZONE, DAY));

        var view = useCase.hold(command(slot));

        assertThat(calls).containsExactly("findSlot", "findConfirmedForOrder", "findActiveForOrder", "tryHold",
                "insertActive");
        assertThat(view.holdId()).isEqualTo(inserted.id().value());
        assertThat(view.orderId()).isEqualTo("order-1");
        assertThat(view.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(view.expiresAt()).isEqualTo(DB_NOW.plus(Duration.ofMinutes(10)));
        assertThat(view.reservationId()).isNull();
        assertThat(view.slot().slotId()).isEqualTo(slot.slotId());
        assertThat(view.slot().priceClp()).isEqualTo(3990L);
        assertThat(published).containsExactly(new ReservationLifecycleEvent(
                ReservationLifecycleEvent.Kind.HOLD_CREATED, 1, Set.of(new ZoneDay(ZONE, DAY))));
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("choque del índice de retención activa: conflicto por carrera, sin evento ni fijar rechazo")
    void insertConflictIsARaceThatRollsBack() {
        var slot = slot(SlotView.Status.AVAILABLE);
        outcome = new HoldCapacityOutcome.Acquired(new ZoneDay(ZONE, DAY));
        insertConflict = true;

        assertThatThrownBy(() -> useCase.hold(command(slot)))
                .isInstanceOfSatisfying(ReservationConflictException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.ACTIVE_HOLD_EXISTS));
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).containsExactly("ACTIVE_HOLD_EXISTS");
        // no es ReservationException: la transacción revierte (incluido el held + 1)
        assertThat(ReservationConflictException.class.getSuperclass()).isNotEqualTo(ReservationException.class);
    }

    private void assertRejection(HoldSlotCommand command, ReservationErrorCode code) {
        assertThatThrownBy(() -> useCase.hold(command))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(code));
        assertThat(rejectedCodes).containsExactly(code.name());
    }

    /** Ejecuta la acción tal cual: la idempotencia tiene sus propias pruebas. */
    private static final class PassThroughIdempotency implements IdempotencyService {
        @Override
        public <T> T execute(IdempotentRequest request, Class<T> resultType, Supplier<T> action) {
            return action.get();
        }
    }
}
