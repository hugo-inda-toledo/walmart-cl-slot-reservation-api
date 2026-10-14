package cl.walmart.slot.reservation.domain.exception;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for domain failures that can be mapped to an API error response.
 */
public abstract class DomainException extends RuntimeException {

    private static final Set<String> RESERVED_DETAIL_KEYS = Set.of(
            "type", "title", "status", "detail", "instance", "code", "correlationId", "errors");

    private final ErrorCode code;
    private final ErrorCategory category;
    private final Map<String, Object> details;

    protected DomainException(ErrorCode code, ErrorCategory category, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.details = immutableDetails(details);
    }

    public ErrorCode code() {
        return code;
    }

    public ErrorCategory category() {
        return category;
    }

    public Map<String, Object> details() {
        return details;
    }

    private static Map<String, Object> immutableDetails(Map<String, Object> details) {
        Objects.requireNonNull(details, "details must not be null");
        details.keySet().forEach(DomainException::validateDetailKey);
        return Map.copyOf(details);
    }

    private static void validateDetailKey(String key) {
        Objects.requireNonNull(key, "detail keys must not be null");
        if (RESERVED_DETAIL_KEYS.contains(key)) {
            throw new IllegalArgumentException("detail key is reserved: " + key);
        }
    }
}
