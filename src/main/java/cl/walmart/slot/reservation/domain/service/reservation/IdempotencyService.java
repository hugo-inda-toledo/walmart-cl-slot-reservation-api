package cl.walmart.slot.reservation.domain.service.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.IdempotentRequest;
import java.util.function.Supplier;

/**
 * Ejecución idempotente de una operación de escritura: reclama la clave del cliente, repite el resultado ya
 * guardado o ejecuta la acción y fija su resultado en la misma transacción del llamador.
 */
public interface IdempotencyService {

    /**
     * Ejecuta {@code action} una sola vez por clave vigente.
     *
     * @param resultType tipo del resultado a reconstruir al repetir una ejecución exitosa
     * @return el resultado de la acción o el resultado guardado de la ejecución original
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code IDEMPOTENCY_KEY_REUSED} si
     *         cambian operación o huella, o el rechazo de negocio (guardado o recién producido) de la acción
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException si otra transacción
     *         procesa la misma clave y la espera se agotó
     */
    <T> T execute(IdempotentRequest request, Class<T> resultType, Supplier<T> action);
}
