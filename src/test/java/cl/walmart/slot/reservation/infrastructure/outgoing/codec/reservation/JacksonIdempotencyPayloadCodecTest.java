package cl.walmart.slot.reservation.infrastructure.outgoing.codec.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class JacksonIdempotencyPayloadCodecTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final JacksonIdempotencyPayloadCodec codec = new JacksonIdempotencyPayloadCodec(mapper);

    private static SlotSnapshotView slot() {
        return new SlotSnapshotView(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2027-01-20"),
                Instant.parse("2027-01-20T13:00:00Z"), Instant.parse("2027-01-20T15:00:00Z"), 2990);
    }

    @Test
    void holdViewRoundTripsIdenticallyWithAndWithoutReservationId() {
        HoldView active = new HoldView(UUID.randomUUID(), "order-1", HoldStatus.ACTIVE,
                Instant.parse("2027-01-10T10:00:00.123456Z"), Instant.parse("2027-01-10T10:10:00.123456Z"), slot(),
                null);
        HoldView confirmed = new HoldView(UUID.randomUUID(), "order-2", HoldStatus.CONFIRMED,
                Instant.parse("2027-01-10T10:00:00Z"), Instant.parse("2027-01-10T10:10:00Z"), slot(),
                UUID.randomUUID());

        assertThat(codec.decode(codec.encode(active), HoldView.class)).isEqualTo(active);
        assertThat(codec.decode(codec.encode(confirmed), HoldView.class)).isEqualTo(confirmed);
    }

    @Test
    void reservationViewRoundTripsIdentically() {
        ReservationView view = new ReservationView(UUID.randomUUID(), UUID.randomUUID(), "order-3",
                ReservationStatus.CANCELLED, Instant.parse("2027-01-10T10:00:00Z"),
                Instant.parse("2027-01-11T10:00:00Z"), slot());

        assertThat(codec.decode(codec.encode(view), ReservationView.class)).isEqualTo(view);
    }

    @Test
    void encodedResultIsVersionedAndTypeTagged() {
        HoldView view = new HoldView(UUID.randomUUID(), "order-1", HoldStatus.ACTIVE,
                Instant.parse("2027-01-10T10:00:00Z"), Instant.parse("2027-01-10T10:10:00Z"), slot(), null);

        var tree = mapper.readTree(codec.encode(view));

        assertThat(tree.get("v").asInt()).isEqualTo(1);
        assertThat(tree.get("type").asString()).isEqualTo("HoldView");
        assertThat(tree.get("body").get("orderId").asString()).isEqualTo("order-1");
    }

    @Test
    void decodeRejectsAnotherTypeOrVersion() {
        String holdPayload = codec.encode(new HoldView(UUID.randomUUID(), "order-1", HoldStatus.ACTIVE,
                Instant.parse("2027-01-10T10:00:00Z"), Instant.parse("2027-01-10T10:10:00Z"), slot(), null));

        assertThatThrownBy(() -> codec.decode(holdPayload, ReservationView.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HoldView");
        assertThatThrownBy(() -> codec.decode(holdPayload.replace("\"v\":1", "\"v\":2"), HoldView.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void slotFullRejectionWithAlternativesRoundTripsIdentically() {
        SlotId slotId = new SlotId(UUID.randomUUID());
        ReservationException original = ReservationException.slotFull(slotId, List.of(
                new AlternativeSlot(new SlotId(UUID.randomUUID()), LocalDate.parse("2027-01-20"),
                        Instant.parse("2027-01-20T15:00:00Z"), Instant.parse("2027-01-20T17:00:00Z"), 3990, 4),
                new AlternativeSlot(new SlotId(UUID.randomUUID()), LocalDate.parse("2027-01-21"),
                        Instant.parse("2027-01-21T13:00:00Z"), Instant.parse("2027-01-21T15:00:00Z"), 5_000_000_000L,
                        1)));

        ReservationException replayed = codec.decodeRejection(codec.encodeRejection(original));

        assertThat(replayed.reservationCode()).isEqualTo(ReservationErrorCode.SLOT_FULL);
        assertThat(replayed.getMessage()).isEqualTo(original.getMessage());
        assertThat(replayed.details()).isEqualTo(original.details());
        assertThat(replayed.category()).isEqualTo(original.category());
    }

    @Test
    void rejectionsWithOptionalAndPlainDetailsRoundTripIdentically() {
        List<ReservationException> rejections = List.of(
                ReservationException.activeHoldExists(null),
                ReservationException.activeHoldExists(HoldId.random()),
                ReservationException.orderAlreadyReserved(ReservationId.random()),
                ReservationException.holdNotActive(HoldId.random(), HoldStatus.CONFIRMED, ReservationId.random()),
                ReservationException.slotNoService(new SlotId(UUID.randomUUID()), LocalDate.parse("2027-01-20")),
                ReservationException.slotCutoffPassed(new SlotId(UUID.randomUUID()),
                        Instant.parse("2027-01-20T11:00:00Z")),
                ReservationException.holdExpired(HoldId.random(), Instant.parse("2027-01-20T11:00:00Z")),
                ReservationException.idempotencyKeyReused());

        for (ReservationException original : rejections) {
            ReservationException replayed = codec.decodeRejection(codec.encodeRejection(original));

            assertThat(replayed.reservationCode()).isEqualTo(original.reservationCode());
            assertThat(replayed.getMessage()).isEqualTo(original.getMessage());
            assertThat(replayed.details()).isEqualTo(original.details());
        }
    }

    @Test
    void rejectionPayloadIsVersionedAndDecodeRejectsUnknownCodes() {
        String payload = codec.encodeRejection(ReservationException.idempotencyKeyReused());

        var tree = mapper.readTree(payload);
        assertThat(tree.get("v").asInt()).isEqualTo(1);
        assertThat(tree.get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        assertThatThrownBy(() -> codec.decodeRejection(payload.replace("IDEMPOTENCY_KEY_REUSED", "NOPE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decodeRejection(payload.replace("\"v\":1", "\"v\":9")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }
}
