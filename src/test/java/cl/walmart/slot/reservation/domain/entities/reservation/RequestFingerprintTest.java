package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void isStableLowercaseSha256Hex() {
        var first = RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-1");
        var second = RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-1");

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).matches("[0-9a-f]{64}");
    }

    @Test
    void matchesTheDocumentedCanonicalForm() {
        // SHA-256("HOLD_SLOT\nslot-1\norder-1")
        var expected = RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-1").value();
        assertThat(expected).isEqualTo(sha256Hex("HOLD_SLOT\nslot-1\norder-1"));
    }

    @Test
    void differsByOperationAndByAnyField() {
        var base = RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-1");

        assertThat(RequestFingerprint.of(IdempotentOperation.CONFIRM_HOLD, "slot-1", "order-1")).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-2", "order-1")).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1", "order-2")).isNotEqualTo(base);
        assertThat(RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "slot-1")).isNotEqualTo(base);
    }

    @Test
    void fieldBoundariesAreNotAmbiguous() {
        assertThat(RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "ab", "c"))
                .isNotEqualTo(RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, "a", "bc"));
    }

    @Test
    void rejectsNullsAndMalformedValues() {
        assertThatThrownBy(() -> RequestFingerprint.of(null, "a")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequestFingerprint.of(IdempotentOperation.HOLD_SLOT, (String) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RequestFingerprint("ABC")).isInstanceOf(IllegalArgumentException.class);
    }

    private static String sha256Hex(String text) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
