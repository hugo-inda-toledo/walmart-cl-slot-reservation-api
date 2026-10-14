package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import cl.walmart.slot.reservation.domain.port.reservation.CurrentCustomerProvider;
import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Identidad simulada: resuelve el cliente desde el encabezado {@code X-Customer-Id} de la petición en curso. En
 * producción se reemplaza por un adaptador que lea el JWT, sin tocar dominio ni casos de uso.
 */
@Component
public final class HeaderCurrentCustomerProvider implements CurrentCustomerProvider {

    public static final String HEADER = "X-Customer-Id";

    @Override
    public CustomerId currentCustomer() {
        String value = currentRequest().getHeader(HEADER);
        if (value == null) {
            throw ReservationException.invalidCustomerId();
        }
        try {
            return new CustomerId(value);
        } catch (IllegalArgumentException invalid) {
            throw ReservationException.invalidCustomerId();
        }
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        throw new IllegalStateException("No hay una petición HTTP en curso para resolver el cliente");
    }
}
