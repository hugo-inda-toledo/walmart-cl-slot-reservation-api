package cl.walmart.slot.reservation.application.usecase.reservation;

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
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ReleaseHoldUseCaseImplTest {

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
    private Map<SlotId, Integer> released;

    private final ReleaseHoldUseCaseImpl useCase = new ReleaseHoldUseCaseImpl(
            () -> CUSTOMER,
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
                    throw new AssertionError();
                }

                @Override
                public Optional<SlotHold> markReleased(HoldId id, CustomerId customer) {
                    calls.add("markReleased");
                    return markResult;
                }

                @Override
                public List<SlotHold> expireDueBatch(int limit) {
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
                    throw new AssertionError();
                }

                @Override
                public Set<ZoneDay> releaseHeld(Map<SlotId, Integer> counts) {
                    calls.add("releaseHeld");
                    released = counts;
                    return Set.of(new ZoneDay(ZONE, DAY));
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

    @Test
    @DisplayName("es el límite transaccional y no revierte por rechazos de negocio")
    void declaresTransactionWithBusinessRejectionsNotRollingBack() {
        var tx = ReleaseHoldUseCaseImpl.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).containsExactly(ReservationException.class);
    }

    @Test
    @DisplayName("éxito: libera con guarda, devuelve un cupo retenido y publica HOLD_RELEASED una vez")
    void success() {
        var active = hold(HoldStatus.ACTIVE, DB_NOW.plusSeconds(300));
        markResult = Optional.of(new SlotHold(active.id(), CUSTOMER, ORDER, SNAPSHOT, HoldStatus.RELEASED,
                active.createdAt(), active.expiresAt(), DB_NOW));

        useCase.release(active.id());

        assertThat(calls).containsExactly("markReleased", "releaseHeld");
        assertThat(released).containsExactly(Map.entry(SNAPSHOT.slotId(), 1));
        assertThat(published).containsExactly(new ReservationLifecycleEvent(
                ReservationLifecycleEvent.Kind.HOLD_RELEASED, 1, Set.of(new ZoneDay(ZONE, DAY))));
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida y retención ausente o ajena: HOLD_NOT_FOUND sin escrituras")
    void notFound() {
        var missing = HoldId.random();

        assertThatThrownBy(() -> useCase.release(missing))
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_NOT_FOUND));
        assertThat(calls).containsExactly("markReleased", "findForCustomer");
        assertThat(rejectedCodes).containsExactly("HOLD_NOT_FOUND");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con retención ya RELEASED: éxito sin cambios")
    void alreadyReleasedIsSuccessWithoutChanges() {
        var done = hold(HoldStatus.RELEASED, DB_NOW.plusSeconds(300));
        found = Optional.of(new HoldRecord(done, Optional.empty(), DB_NOW));

        useCase.release(done.id());

        assertThat(calls).containsExactly("markReleased", "findForCustomer");
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con retención ya EXPIRED por el barredor: éxito sin cambios")
    void alreadyExpiredIsSuccessWithoutChanges() {
        var done = hold(HoldStatus.EXPIRED, DB_NOW.minusSeconds(60));
        found = Optional.of(new HoldRecord(done, Optional.empty(), DB_NOW));

        useCase.release(done.id());

        assertThat(calls).doesNotContain("releaseHeld");
        assertThat(published).isEmpty();
        assertThat(rejectedCodes).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida con retención CONFIRMED: HOLD_NOT_ACTIVE con holdStatus y reservationId")
    void confirmedIsNotActive() {
        var confirmed = hold(HoldStatus.CONFIRMED, DB_NOW.plusSeconds(300));
        var reservationId = ReservationId.random();
        found = Optional.of(new HoldRecord(confirmed, Optional.of(reservationId), DB_NOW));

        assertThatThrownBy(() -> useCase.release(confirmed.id()))
                .isInstanceOfSatisfying(ReservationException.class, e -> {
                    assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_NOT_ACTIVE);
                    assertThat(e.details()).containsEntry("holdStatus", "CONFIRMED")
                            .containsEntry("reservationId", reservationId.value().toString());
                });
        assertThat(rejectedCodes).containsExactly("HOLD_NOT_ACTIVE");
        assertThat(calls).doesNotContain("releaseHeld");
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("guarda fallida pero la retención figura ACTIVE: estado inconsistente, error interno")
    void activeAfterFailedGuardIsInternalError() {
        var active = hold(HoldStatus.ACTIVE, DB_NOW.plusSeconds(300));
        found = Optional.of(new HoldRecord(active, Optional.empty(), DB_NOW));

        assertThatThrownBy(() -> useCase.release(active.id())).isInstanceOf(IllegalStateException.class);
        assertThat(published).isEmpty();
    }
}
