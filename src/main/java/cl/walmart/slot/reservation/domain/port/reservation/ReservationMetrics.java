package cl.walmart.slot.reservation.domain.port.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.ReservationLifecycleEvent;

/** Métricas de reservas; la implementación vive en infraestructura. */
public interface ReservationMetrics {

    void lifecycle(ReservationLifecycleEvent.Kind kind, int count);

    void holdRejected(String code);

    /** {@code result}: {@code replayed}, {@code key_reused} o {@code in_progress}. */
    void idempotency(String result);
}
