package cl.walmart.slot.reservation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ApplicationEntryPointTest {

    @Test
    void exposesTheSpringBootApplicationEntryPoint() {
        assertDoesNotThrow(() -> Class.forName("cl.walmart.slot.reservation.Application"));
    }
}
