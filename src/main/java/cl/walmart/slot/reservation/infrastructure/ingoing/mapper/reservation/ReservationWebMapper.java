package cl.walmart.slot.reservation.infrastructure.ingoing.mapper.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldView;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationView;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotSnapshotView;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationHoldStatusDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotSnapshotDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;

/**
 * Traduce las vistas de reservas a los DTOs del contrato HTTP: fechas-hora con el desfase de
 * {@code America/Santiago} y precios CLP enteros. El {@code timeZone} del snapshot no se persiste; se completa aquí.
 */
@Mapper
public interface ReservationWebMapper {

    ZoneId BUSINESS_ZONE = ZoneId.of("America/Santiago");

    default ReservationSlotHoldDto toDto(HoldView view) {
        var dto = new ReservationSlotHoldDto(view.holdId(), view.orderId(),
                ReservationHoldStatusDto.valueOf(view.status().name()), local(view.createdAt()),
                local(view.expiresAt()), snapshot(view.slot()));
        dto.setReservationId(view.reservationId());
        return dto;
    }

    default ReservationBookingDto toDto(ReservationView view) {
        var dto = new ReservationBookingDto(view.reservationId(), view.holdId(), view.orderId(),
                ReservationBookingStatusDto.valueOf(view.status().name()), local(view.confirmedAt()),
                snapshot(view.slot()));
        dto.setCancelledAt(view.cancelledAt() == null ? null : local(view.cancelledAt()));
        return dto;
    }

    private static ReservationSlotSnapshotDto snapshot(SlotSnapshotView slot) {
        return new ReservationSlotSnapshotDto(slot.slotId(), slot.zoneId(), slot.deliveryDate(), local(slot.startAt()),
                local(slot.endAt()), slot.priceClp(), ReservationSlotSnapshotDto.TimeZoneEnum.AMERICA_SANTIAGO);
    }

    private static OffsetDateTime local(Instant instant) {
        return OffsetDateTime.ofInstant(instant, BUSINESS_ZONE);
    }
}
