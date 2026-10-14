package cl.walmart.slot.reservation.domain.exception.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.AlternativeSlot;
import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.enums.reservation.HoldStatus;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.entities.reservation.SlotId;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rechazo de negocio de reservas detectado antes de escribir efectos: es fijable en la clave de idempotencia y
 * su transacción hace commit. Se crea mediante las fábricas por código. Los instantes se informan como texto
 * ISO-8601 UTC y los identificadores como texto; los detalles opcionales se omiten cuando no existen.
 */
public final class ReservationException extends DomainException {

    private final ReservationErrorCode reservationCode;

    private ReservationException(ReservationErrorCode code, String message, Map<String, Object> details) {
        super(code, code.category(), message, details);
        this.reservationCode = code;
    }

    public ReservationErrorCode reservationCode() {
        return reservationCode;
    }

    public static ReservationException invalidCustomerId() {
        return new ReservationException(ReservationErrorCode.INVALID_CUSTOMER_ID,
                "El encabezado X-Customer-Id falta o no es válido.", Map.of());
    }

    public static ReservationException slotNotFound(SlotId slotId) {
        Objects.requireNonNull(slotId, "slotId must not be null");
        return new ReservationException(ReservationErrorCode.SLOT_NOT_FOUND,
                "La ventana de despacho no existe.", Map.of("slotId", slotId.value().toString()));
    }

    public static ReservationException slotNoService(SlotId slotId, LocalDate deliveryDate) {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(deliveryDate, "deliveryDate must not be null");
        return new ReservationException(ReservationErrorCode.SLOT_NO_SERVICE,
                "La ventana corresponde a un día sin servicio de despacho.",
                Map.of("slotId", slotId.value().toString(), "deliveryDate", deliveryDate.toString()));
    }

    public static ReservationException slotCutoffPassed(SlotId slotId, Instant cutoffAt) {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        return new ReservationException(ReservationErrorCode.SLOT_CUTOFF_PASSED,
                "La ventana ya superó su hora de cierre.",
                Map.of("slotId", slotId.value().toString(), "cutoffAt", cutoffAt.toString()));
    }

    public static ReservationException slotFull(SlotId slotId, List<AlternativeSlot> alternatives) {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(alternatives, "alternatives must not be null");
        var items = alternatives.stream().map(ReservationException::alternativeDetail).toList();
        return new ReservationException(ReservationErrorCode.SLOT_FULL,
                "La ventana no tiene cupos disponibles.",
                Map.of("slotId", slotId.value().toString(), "alternatives", items));
    }

    /** {@code holdId} es opcional: se omite cuando es {@code null}. */
    public static ReservationException activeHoldExists(HoldId holdId) {
        return new ReservationException(ReservationErrorCode.ACTIVE_HOLD_EXISTS,
                ACTIVE_HOLD_EXISTS_MESSAGE, optional("holdId", holdId == null ? null : holdId.value().toString()));
    }

    /** {@code reservationId} es opcional: se omite cuando es {@code null}. */
    public static ReservationException orderAlreadyReserved(ReservationId reservationId) {
        return new ReservationException(ReservationErrorCode.ORDER_ALREADY_RESERVED,
                ORDER_ALREADY_RESERVED_MESSAGE,
                optional("reservationId", reservationId == null ? null : reservationId.value().toString()));
    }

    public static ReservationException holdNotFound(HoldId holdId) {
        Objects.requireNonNull(holdId, "holdId must not be null");
        return new ReservationException(ReservationErrorCode.HOLD_NOT_FOUND,
                "La retención no existe.", Map.of("holdId", holdId.value().toString()));
    }

    public static ReservationException holdExpired(HoldId holdId, Instant expiresAt) {
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return new ReservationException(ReservationErrorCode.HOLD_EXPIRED,
                "La retención venció.",
                Map.of("holdId", holdId.value().toString(), "expiresAt", expiresAt.toString()));
    }

    /** {@code reservationId} es opcional: solo existe si la retención fue confirmada. */
    public static ReservationException holdNotActive(HoldId holdId, HoldStatus holdStatus,
                                                     ReservationId reservationId) {
        Objects.requireNonNull(holdId, "holdId must not be null");
        Objects.requireNonNull(holdStatus, "holdStatus must not be null");
        var details = new HashMap<String, Object>();
        details.put("holdId", holdId.value().toString());
        details.put("holdStatus", holdStatus.name());
        if (reservationId != null) {
            details.put("reservationId", reservationId.value().toString());
        }
        return new ReservationException(ReservationErrorCode.HOLD_NOT_ACTIVE,
                "La retención ya no está activa.", details);
    }

    public static ReservationException reservationNotFound(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND,
                "La reserva no existe.", Map.of("reservationId", reservationId.value().toString()));
    }

    public static ReservationException reservationCancellationClosed(ReservationId reservationId, Instant cutoffAt) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(cutoffAt, "cutoffAt must not be null");
        return new ReservationException(ReservationErrorCode.RESERVATION_CANCELLATION_CLOSED,
                "La reserva ya no puede cancelarse porque superó la hora de cierre de su ventana.",
                Map.of("reservationId", reservationId.value().toString(), "cutoffAt", cutoffAt.toString()));
    }

    public static ReservationException idempotencyKeyReused() {
        return new ReservationException(ReservationErrorCode.IDEMPOTENCY_KEY_REUSED,
                "La clave de idempotencia ya se usó con una petición distinta.", Map.of());
    }

    /** Reconstruye un rechazo guardado en la clave de idempotencia para reproducirlo tal cual. */
    public static ReservationException replayed(ReservationErrorCode code, String message,
                                                Map<String, Object> details) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        return new ReservationException(code, message, details);
    }

    static final String ACTIVE_HOLD_EXISTS_MESSAGE = "La orden ya tiene una retención activa.";
    static final String ORDER_ALREADY_RESERVED_MESSAGE = "La orden ya tiene una reserva confirmada.";

    private static Map<String, Object> optional(String key, String value) {
        return value == null ? Map.of() : Map.of(key, value);
    }

    private static Map<String, Object> alternativeDetail(AlternativeSlot alternative) {
        var item = new LinkedHashMap<String, Object>();
        item.put("slotId", alternative.slotId().value().toString());
        item.put("deliveryDate", alternative.deliveryDate().toString());
        item.put("start", alternative.startAt().toString());
        item.put("end", alternative.endAt().toString());
        item.put("price", alternative.priceClp());
        item.put("remainingCapacity", alternative.remainingCapacity());
        return item;
    }
}
