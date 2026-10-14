package cl.walmart.slot.reservation.domain.exception.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.exception.DomainException;
import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReservationErrorsTest {

    private static final Set<String> RESERVED =
            Set.of("type", "title", "status", "detail", "instance", "code", "correlationId", "errors");
    private static final SlotId SLOT = new SlotId(UUID.randomUUID());
    private static final HoldId HOLD = HoldId.random();
    private static final ReservationId RESERVATION = ReservationId.random();
    private static final Instant INSTANT = Instant.parse("2030-01-10T10:00:00Z");

    private static final List<Supplier<ReservationException>> FACTORIES = List.of(
            ReservationException::invalidCustomerId,
            () -> ReservationException.slotNotFound(SLOT),
            () -> ReservationException.slotNoService(SLOT, LocalDate.of(2030, 1, 10)),
            () -> ReservationException.slotCutoffPassed(SLOT, INSTANT),
            () -> ReservationException.slotFull(SLOT, List.of()),
            () -> ReservationException.slotFull(SLOT, List.of(new AlternativeSlot(SLOT, LocalDate.of(2030, 1, 10),
                    INSTANT, INSTANT.plusSeconds(3600), 3990, 2))),
            () -> ReservationException.activeHoldExists(HOLD),
            () -> ReservationException.activeHoldExists(null),
            () -> ReservationException.orderAlreadyReserved(RESERVATION),
            () -> ReservationException.orderAlreadyReserved(null),
            () -> ReservationException.holdNotFound(HOLD),
            () -> ReservationException.holdExpired(HOLD, INSTANT),
            () -> ReservationException.holdNotActive(HOLD, HoldStatus.CONFIRMED, RESERVATION),
            () -> ReservationException.holdNotActive(HOLD, HoldStatus.RELEASED, null),
            () -> ReservationException.reservationNotFound(RESERVATION),
            () -> ReservationException.reservationCancellationClosed(RESERVATION, INSTANT),
            ReservationException::idempotencyKeyReused);

    @Test
    void everyFactoryBuildsWithoutReservedKeysOrNullValues() {
        for (var factory : FACTORIES) {
            var e = factory.get();
            assertThat(e.details().keySet()).doesNotContainAnyElementsOf(RESERVED);
            assertThat(e.details().values()).doesNotContainNull();
            assertThat(e.getMessage()).isNotBlank();
        }
    }

    @Test
    void categoryFollowsTheDesignTable() {
        assertCategory(ReservationErrorCode.INVALID_CUSTOMER_ID, ErrorCategory.INVALID_INPUT);
        assertCategory(ReservationErrorCode.SLOT_NOT_FOUND, ErrorCategory.NOT_FOUND);
        assertCategory(ReservationErrorCode.SLOT_NO_SERVICE, ErrorCategory.BUSINESS_RULE);
        assertCategory(ReservationErrorCode.SLOT_CUTOFF_PASSED, ErrorCategory.BUSINESS_RULE);
        assertCategory(ReservationErrorCode.SLOT_FULL, ErrorCategory.CONFLICT);
        assertCategory(ReservationErrorCode.ACTIVE_HOLD_EXISTS, ErrorCategory.CONFLICT);
        assertCategory(ReservationErrorCode.ORDER_ALREADY_RESERVED, ErrorCategory.CONFLICT);
        assertCategory(ReservationErrorCode.HOLD_NOT_FOUND, ErrorCategory.NOT_FOUND);
        assertCategory(ReservationErrorCode.HOLD_EXPIRED, ErrorCategory.CONFLICT);
        assertCategory(ReservationErrorCode.HOLD_NOT_ACTIVE, ErrorCategory.CONFLICT);
        assertCategory(ReservationErrorCode.RESERVATION_NOT_FOUND, ErrorCategory.NOT_FOUND);
        assertCategory(ReservationErrorCode.RESERVATION_CANCELLATION_CLOSED, ErrorCategory.BUSINESS_RULE);
        assertCategory(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED, ErrorCategory.BUSINESS_RULE);
        assertCategory(ReservationErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, ErrorCategory.CONFLICT);
        assertThat(ReservationErrorCode.values()).hasSize(14);
    }

    private static void assertCategory(ReservationErrorCode code, ErrorCategory category) {
        assertThat(code.category()).isEqualTo(category);
        assertThat(code.value()).isEqualTo(code.name());
    }

    @Test
    void factoriesProduceTheirOwnCodeAndCategory() {
        var e = ReservationException.holdExpired(HOLD, INSTANT);

        assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.HOLD_EXPIRED);
        assertThat(e.code()).isEqualTo(ReservationErrorCode.HOLD_EXPIRED);
        assertThat(e.category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(e.details()).containsEntry("holdId", HOLD.value().toString())
                .containsEntry("expiresAt", "2030-01-10T10:00:00Z");
    }

    @Test
    void holdNotActiveUsesHoldStatusAndOmitsAbsentReservationId() {
        var withReservation = ReservationException.holdNotActive(HOLD, HoldStatus.CONFIRMED, RESERVATION);
        var without = ReservationException.holdNotActive(HOLD, HoldStatus.RELEASED, null);

        assertThat(withReservation.details()).containsEntry("holdStatus", "CONFIRMED")
                .containsEntry("reservationId", RESERVATION.value().toString()).doesNotContainKey("status");
        assertThat(without.details()).containsOnlyKeys("holdId", "holdStatus");
    }

    @Test
    void optionalDetailsAreOmittedWhenAbsent() {
        assertThat(ReservationException.activeHoldExists(null).details()).isEmpty();
        assertThat(ReservationException.orderAlreadyReserved(null).details()).isEmpty();
        assertThat(ReservationException.activeHoldExists(HOLD).details()).containsOnlyKeys("holdId");
    }

    @Test
    void slotFullCarriesAlternativesAsPlainMaps() {
        var alternative = new AlternativeSlot(new SlotId(UUID.randomUUID()), LocalDate.of(2030, 1, 10),
                INSTANT, INSTANT.plusSeconds(3600), 3990, 2);

        var e = ReservationException.slotFull(SLOT, List.of(alternative));

        assertThat(e.details()).containsEntry("slotId", SLOT.value().toString());
        @SuppressWarnings("unchecked")
        var alternatives = (List<Map<String, Object>>) e.details().get("alternatives");
        assertThat(alternatives).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("slotId", alternative.slotId().value().toString())
                .containsEntry("deliveryDate", "2030-01-10")
                .containsEntry("start", "2030-01-10T10:00:00Z")
                .containsEntry("end", "2030-01-10T11:00:00Z")
                .containsEntry("price", 3990L)
                .containsEntry("remainingCapacity", 2));
    }

    @Test
    void replayedRebuildsAnEquivalentException() {
        var original = ReservationException.holdNotActive(HOLD, HoldStatus.CONFIRMED, RESERVATION);

        var rebuilt = ReservationException.replayed(original.reservationCode(), original.getMessage(),
                original.details());

        assertThat(rebuilt.code()).isEqualTo(original.code());
        assertThat(rebuilt.category()).isEqualTo(original.category());
        assertThat(rebuilt.getMessage()).isEqualTo(original.getMessage());
        assertThat(rebuilt.details()).isEqualTo(original.details());
    }

    @Test
    void conflictExceptionIsNotARejectionAndOmitsAbsentDetails() {
        var conflict = ReservationConflictException.activeHoldExists(null);
        var reserved = ReservationConflictException.orderAlreadyReserved(RESERVATION);

        assertThat(conflict).isNotInstanceOf(ReservationException.class).isInstanceOf(DomainException.class);
        assertThat(conflict.reservationCode()).isEqualTo(ReservationErrorCode.ACTIVE_HOLD_EXISTS);
        assertThat(conflict.category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(conflict.details()).isEmpty();
        assertThat(reserved.reservationCode()).isEqualTo(ReservationErrorCode.ORDER_ALREADY_RESERVED);
        assertThat(reserved.details()).containsOnlyKeys("reservationId");
    }

    @Test
    void inProgressExceptionSuggestsRetryAndIsNotARejection() {
        var e = new IdempotencyInProgressException(3);

        assertThat(e).isNotInstanceOf(ReservationException.class);
        assertThat(e.retryAfterSeconds()).isEqualTo(3);
        assertThat(e.code()).isEqualTo(ReservationErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        assertThat(e.category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(e.details()).containsEntry("retryAfterSeconds", 3);
        assertThatThrownBy(() -> new IdempotencyInProgressException(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
