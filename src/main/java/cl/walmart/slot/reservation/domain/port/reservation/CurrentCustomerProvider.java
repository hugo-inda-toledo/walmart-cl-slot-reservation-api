package cl.walmart.slot.reservation.domain.port.reservation;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;

/** Identidad simulada del cliente: la resuelve la infraestructura y se reemplaza (p. ej. por JWT) sin tocar casos de uso. */
public interface CurrentCustomerProvider {

    /**
     * Cliente de la petición en curso.
     *
     * @throws cl.walmart.slot.reservation.domain.exception.reservation.ReservationException {@code INVALID_CUSTOMER_ID} si falta o
     *         es inválido
     */
    CustomerId currentCustomer();
}
