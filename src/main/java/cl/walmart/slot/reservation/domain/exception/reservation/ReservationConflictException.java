package cl.walmart.slot.reservation.domain.exception.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.HoldId;
import cl.walmart.slot.reservation.domain.entities.reservation.ReservationId;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import java.util.Map;

/**
 * Conflicto que solo detecta una restricción única después de escribir efectos (carrera entre claves distintas de la
 * misma orden). No es un {@link ReservationException}: la transacción se revierte por completo y no se fija en la
 * clave de idempotencia; el reintento llega de forma determinista al mismo código por la vía previa.
 */
public final class ReservationConflictException extends DomainException {

    private ReservationConflictException(ReservationErrorCode code, String message, Map<String, Object> details) {
        super(code, code.category(), message, details);
    }

    public ReservationErrorCode reservationCode() {
        return (ReservationErrorCode) code();
    }

    /** {@code holdId} es opcional (la retención ganadora puede no conocerse): se omite cuando es {@code null}. */
    public static ReservationConflictException activeHoldExists(HoldId holdId) {
        return new ReservationConflictException(ReservationErrorCode.ACTIVE_HOLD_EXISTS,
                ReservationException.ACTIVE_HOLD_EXISTS_MESSAGE,
                holdId == null ? Map.of() : Map.of("holdId", holdId.value().toString()));
    }

    /** {@code reservationId} es opcional: se omite cuando es {@code null}. */
    public static ReservationConflictException orderAlreadyReserved(ReservationId reservationId) {
        return new ReservationConflictException(ReservationErrorCode.ORDER_ALREADY_RESERVED,
                ReservationException.ORDER_ALREADY_RESERVED_MESSAGE,
                reservationId == null ? Map.of() : Map.of("reservationId", reservationId.value().toString()));
    }
}
