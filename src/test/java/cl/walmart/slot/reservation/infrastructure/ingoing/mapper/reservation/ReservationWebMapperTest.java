package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.enums.reservation.ReservationStatus;
import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.reservation.ReservationWebMapper;
import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.reservation.ReservationWebMapperImpl;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationHoldStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotSnapshotDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationWebMapperTest {

    private final ReservationWebMapper mapper = new ReservationWebMapperImpl();

    private final SlotSnapshotView slot = new SlotSnapshotView(UUID.randomUUID(), UUID.randomUUID(),
            LocalDate.of(2030, 1, 2), Instant.parse("2030-01-02T19:00:00Z"), Instant.parse("2030-01-02T21:00:00Z"), 2990L);

    @Test
    void mapeaUnaRetencionActivaConDesfaseDeSantiagoYPrecioEntero() {
        var view = new HoldView(UUID.randomUUID(), "ORD-1", HoldStatus.ACTIVE, Instant.parse("2030-01-01T15:00:00Z"),
                Instant.parse("2030-01-01T15:10:00Z"), slot, null);

        var dto = mapper.toDto(view);

        assertThat(dto.getHoldId()).isEqualTo(view.holdId());
        assertThat(dto.getOrderId()).isEqualTo("ORD-1");
        assertThat(dto.getStatus()).isEqualTo(ReservationHoldStatusDto.ACTIVE);
        assertThat(dto.getCreatedAt().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
        assertThat(dto.getExpiresAt().toInstant()).isEqualTo(view.expiresAt());
        assertThat(dto.getReservationId()).isNull();
        assertThat(dto.getSlot().getSlotId()).isEqualTo(slot.slotId());
        assertThat(dto.getSlot().getZoneId()).isEqualTo(slot.zoneId());
        assertThat(dto.getSlot().getDeliveryDate()).isEqualTo(LocalDate.of(2030, 1, 2));
        assertThat(dto.getSlot().getStart().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
        assertThat(dto.getSlot().getStart().toInstant()).isEqualTo(slot.startAt());
        assertThat(dto.getSlot().getEnd().toInstant()).isEqualTo(slot.endAt());
        assertThat(dto.getSlot().getPrice()).isEqualTo(2990L);
        assertThat(dto.getSlot().getTimeZone()).isEqualTo(ReservationSlotSnapshotDto.TimeZoneEnum.AMERICA_SANTIAGO);
    }

    @Test
    void incluyeLaReservaEnUnaRetencionConfirmada() {
        UUID reservationId = UUID.randomUUID();
        var view = new HoldView(UUID.randomUUID(), "ORD-2", HoldStatus.CONFIRMED, Instant.parse("2030-01-01T15:00:00Z"),
                Instant.parse("2030-01-01T15:10:00Z"), slot, reservationId);

        var dto = mapper.toDto(view);

        assertThat(dto.getStatus()).isEqualTo(ReservationHoldStatusDto.CONFIRMED);
        assertThat(dto.getReservationId()).isEqualTo(reservationId);
    }

    @Test
    void mapeaUnaReservaConfirmadaSinFechaDeCancelacion() {
        var view = new ReservationView(UUID.randomUUID(), UUID.randomUUID(), "ORD-3", ReservationStatus.CONFIRMED,
                Instant.parse("2030-01-01T15:05:00Z"), null, slot);

        var dto = mapper.toDto(view);

        assertThat(dto.getReservationId()).isEqualTo(view.reservationId());
        assertThat(dto.getHoldId()).isEqualTo(view.holdId());
        assertThat(dto.getStatus()).isEqualTo(ReservationBookingStatusDto.CONFIRMED);
        assertThat(dto.getConfirmedAt().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
        assertThat(dto.getCancelledAt()).isNull();
        assertThat(dto.getSlot().getPrice()).isEqualTo(2990L);
    }

    @Test
    void mapeaUnaReservaCanceladaConSuInstante() {
        var view = new ReservationView(UUID.randomUUID(), UUID.randomUUID(), "ORD-4", ReservationStatus.CANCELLED,
                Instant.parse("2030-01-01T15:05:00Z"), Instant.parse("2030-01-01T16:00:00Z"), slot);

        var dto = mapper.toDto(view);

        assertThat(dto.getStatus()).isEqualTo(ReservationBookingStatusDto.CANCELLED);
        assertThat(dto.getCancelledAt().toInstant()).isEqualTo(Instant.parse("2030-01-01T16:00:00Z"));
    }
}
