package cl.walmart.slot.reservation.domain.port.reservation;

import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;

/** Serialización de los resultados y rechazos que se fijan en una clave de idempotencia. */
public interface IdempotencyPayloadCodec {

    String encode(Object result);

    <T> T decode(String payload, Class<T> type);

    String encodeRejection(ReservationException rejection);

    ReservationException decodeRejection(String payload);
}
