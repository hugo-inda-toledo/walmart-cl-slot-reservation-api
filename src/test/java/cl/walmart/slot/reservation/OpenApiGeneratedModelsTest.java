package cl.walmart.slot.reservation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class OpenApiGeneratedModelsTest {

    @Test
    void generatesTheCommonProblemDetailsModels() {
        assertDoesNotThrow(() -> Class.forName(
                "cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto"));
        assertDoesNotThrow(() -> Class.forName(
                "cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.FieldErrorDto"));
    }
}
