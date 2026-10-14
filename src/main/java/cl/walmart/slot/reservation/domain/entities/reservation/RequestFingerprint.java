package cl.walmart.slot.reservation.domain.entities.reservation;

import cl.walmart.slot.reservation.domain.enums.reservation.IdempotentOperation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Huella SHA-256 (hex minúscula, 64 caracteres) de la operación y sus campos canónicos. */
public record RequestFingerprint(String value) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public RequestFingerprint {
        Objects.requireNonNull(value, "value must not be null");
        if (!HEX_64.matcher(value).matches()) {
            throw new IllegalArgumentException("fingerprint must be 64 lowercase hex characters");
        }
    }

    /** Hash de {@code operación + "\n" + campos unidos por "\n"}: hold usa slotId y orderId; confirmación, holdId. */
    public static RequestFingerprint of(IdempotentOperation operation, String... canonicalFields) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(canonicalFields, "canonicalFields must not be null");
        for (String field : canonicalFields) {
            Objects.requireNonNull(field, "canonical fields must not be null");
        }
        var canonical = operation.name() + "\n" + String.join("\n", canonicalFields);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new RequestFingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
