package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import cl.walmart.slot.reservation.infrastructure.ingoing.mapper.reservation.ReservationWebMapper;
import cl.walmart.slot.reservation.domain.usecase.reservation.CancelReservationUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.ConfirmHoldCommand;
import cl.walmart.slot.reservation.domain.usecase.reservation.ConfirmHoldUseCase;
import cl.walmart.slot.reservation.domain.usecase.reservation.GetHoldUseCase;
import cl.walmart.slot.reservation.domain.usecase.reservation.GetReservationUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldSlotCommand;
import cl.walmart.slot.reservation.domain.usecase.reservation.HoldSlotUseCase;
import cl.walmart.slot.reservation.domain.usecase.reservation.ReleaseHoldUseCase;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.IdempotencyKey;
import cl.walmart.slot.reservation.domain.entities.reservation.OrderId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.api.ReservationApi;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationBookingDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationCreateHoldRequestDto;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ReservationSlotHoldDto;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador HTTP de reservas: traduce parámetros a comandos y vistas a DTOs. El cliente no se toma del parámetro
 * {@code xCustomerId} generado: lo resuelve el puerto {@code CurrentCustomerProvider} dentro de los casos de uso.
 */
@RestController
public class ReservationController implements ReservationApi {

    private static final String HOLDS_PATH = "/api/v1/slot-holds/";
    private static final String RESERVATIONS_PATH = "/api/v1/reservations/";

    private final HoldSlotUseCase holdSlot;
    private final GetHoldUseCase getHold;
    private final ReleaseHoldUseCase releaseHold;
    private final ConfirmHoldUseCase confirmHold;
    private final GetReservationUseCase getReservation;
    private final CancelReservationUseCase cancelReservation;
    private final ReservationWebMapper mapper;

    public ReservationController(HoldSlotUseCase holdSlot, GetHoldUseCase getHold, ReleaseHoldUseCase releaseHold,
                                 ConfirmHoldUseCase confirmHold, GetReservationUseCase getReservation,
                                 CancelReservationUseCase cancelReservation, ReservationWebMapper mapper) {
        this.holdSlot = holdSlot;
        this.getHold = getHold;
        this.releaseHold = releaseHold;
        this.confirmHold = confirmHold;
        this.getReservation = getReservation;
        this.cancelReservation = cancelReservation;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<ReservationSlotHoldDto> createSlotHold(String xCustomerId, String idempotencyKey,
                                                                 ReservationCreateHoldRequestDto request) {
        var command = new HoldSlotCommand(new SlotId(request.getSlotId()), new OrderId(request.getOrderId()),
                new IdempotencyKey(idempotencyKey));
        ReservationSlotHoldDto body = mapper.toDto(holdSlot.hold(command));
        return ResponseEntity.created(URI.create(HOLDS_PATH + body.getHoldId())).body(body);
    }

    @Override
    public ResponseEntity<ReservationSlotHoldDto> getSlotHold(String xCustomerId, UUID holdId) {
        return ResponseEntity.ok(mapper.toDto(getHold.get(new HoldId(holdId))));
    }

    @Override
    public ResponseEntity<Void> releaseSlotHold(String xCustomerId, UUID holdId) {
        releaseHold.release(new HoldId(holdId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ReservationBookingDto> confirmSlotHold(String xCustomerId, UUID holdId,
                                                                 String idempotencyKey) {
        var command = new ConfirmHoldCommand(new HoldId(holdId), new IdempotencyKey(idempotencyKey));
        ReservationBookingDto body = mapper.toDto(confirmHold.confirm(command));
        return ResponseEntity.created(URI.create(RESERVATIONS_PATH + body.getReservationId())).body(body);
    }

    @Override
    public ResponseEntity<ReservationBookingDto> getReservation(String xCustomerId, UUID reservationId) {
        return ResponseEntity.ok(mapper.toDto(getReservation.get(new ReservationId(reservationId))));
    }

    @Override
    public ResponseEntity<ReservationBookingDto> cancelReservation(String xCustomerId, UUID reservationId) {
        return ResponseEntity.ok(mapper.toDto(cancelReservation.cancel(new ReservationId(reservationId))));
    }
}
