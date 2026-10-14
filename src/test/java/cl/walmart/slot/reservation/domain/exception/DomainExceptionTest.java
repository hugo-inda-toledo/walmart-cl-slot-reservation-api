package cl.walmart.slot.reservation.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DomainExceptionTest {

    @Test
    void exposesAnImmutableSnapshotOfDetails() {
        var suppliedDetails = new HashMap<String, Object>();
        suppliedDetails.put("slotId", "slot-123");

        var exception = new TestDomainException(TestErrorCode.SLOT_FULL, ErrorCategory.CONFLICT, suppliedDetails);
        suppliedDetails.put("capacity", 0);

        assertThat(exception.code()).isEqualTo(TestErrorCode.SLOT_FULL);
        assertThat(exception.category()).isEqualTo(ErrorCategory.CONFLICT);
        assertThat(exception.details()).containsExactly(Map.entry("slotId", "slot-123"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> exception.details().put("newDetail", "value"));
    }

    @ParameterizedTest
    @MethodSource("reservedDetailKeys")
    void rejectsDetailsThatUseReservedProblemDetailFields(String reservedKey) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TestDomainException(
                        TestErrorCode.SLOT_FULL,
                        ErrorCategory.CONFLICT,
                        Map.of(reservedKey, "value")));
    }

    @Test
    void rejectsNullDetailKeys() {
        var details = new HashMap<String, Object>();
        details.put(null, "value");

        assertThatNullPointerException()
                .isThrownBy(() -> new TestDomainException(TestErrorCode.SLOT_FULL, ErrorCategory.CONFLICT, details));
    }

    @Test
    void rejectsNullCodeAndCategory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TestDomainException(null, ErrorCategory.CONFLICT, Map.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new TestDomainException(TestErrorCode.SLOT_FULL, null, Map.of()));
    }

    private static Stream<String> reservedDetailKeys() {
        return Stream.of("type", "title", "status", "detail", "instance", "code", "correlationId", "errors");
    }

    private enum TestErrorCode implements ErrorCode {
        SLOT_FULL;

        @Override
        public String value() {
            return name();
        }
    }

    private static final class TestDomainException extends DomainException {

        private TestDomainException(ErrorCode code, ErrorCategory category, Map<String, Object> details) {
            super(code, category, "The delivery slot is full.", details);
        }
    }
}
