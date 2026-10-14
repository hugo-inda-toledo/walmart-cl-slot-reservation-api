package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.walmart.slot.reservation.domain.entities.reservation.CustomerId;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HeaderCurrentCustomerProviderTest {

    private final HeaderCurrentCustomerProvider provider = new HeaderCurrentCustomerProvider();

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void devuelveElClienteDelEncabezadoDeLaPeticionEnCurso() {
        bind("customer-1.A_b");

        assertThat(provider.currentCustomer()).isEqualTo(new CustomerId("customer-1.A_b"));
    }

    @Test
    void rechazaEncabezadoAusenteConInvalidCustomerId() {
        bind(null);

        assertInvalidCustomerId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "cliente con espacios", "cliente/1", "ñandú"})
    void rechazaEncabezadoInvalidoConInvalidCustomerId(String value) {
        bind(value);

        assertInvalidCustomerId();
    }

    @Test
    void rechazaEncabezadoMasLargoQueElLimite() {
        bind("a".repeat(65));

        assertInvalidCustomerId();
    }

    @Test
    void fallaConEstadoIlegalSiNoHayPeticionEnCurso() {
        assertThatThrownBy(provider::currentCustomer).isInstanceOf(IllegalStateException.class);
    }

    private void assertInvalidCustomerId() {
        assertThatThrownBy(provider::currentCustomer)
                .isInstanceOfSatisfying(ReservationException.class,
                        e -> assertThat(e.reservationCode()).isEqualTo(ReservationErrorCode.INVALID_CUSTOMER_ID));
    }

    private static void bind(String customerId) {
        var request = new MockHttpServletRequest();
        if (customerId != null) {
            request.addHeader("X-Customer-Id", customerId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
