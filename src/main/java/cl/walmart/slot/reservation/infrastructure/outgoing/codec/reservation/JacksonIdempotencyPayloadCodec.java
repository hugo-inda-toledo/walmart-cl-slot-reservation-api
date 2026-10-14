package cl.walmart.slot.reservation.infrastructure.outgoing.codec.reservation;

import cl.walmart.slot.reservation.domain.port.reservation.IdempotencyPayloadCodec;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationErrorCode;
import cl.walmart.slot.reservation.domain.exception.reservation.ReservationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serializa con el {@link JsonMapper} de la aplicación los resultados y rechazos que se fijan en una clave de
 * idempotencia. Resultado: {@code {"v":1,"type":<clase simple>,"body":{...}}}; rechazo:
 * {@code {"v":1,"code","message","details"}}. La versión permite evolucionar el formato sin romper claves
 * vigentes; una versión o tipo desconocido falla en vez de reproducir un resultado equivocado.
 */
@Component
class JacksonIdempotencyPayloadCodec implements IdempotencyPayloadCodec {

    static final int VERSION = 1;

    private static final TypeReference<LinkedHashMap<String, Object>> DETAILS_TYPE = new TypeReference<>() {
    };

    private final JsonMapper mapper;

    JacksonIdempotencyPayloadCodec(JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public String encode(Object result) {
        Objects.requireNonNull(result, "result must not be null");
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("v", VERSION);
        envelope.put("type", result.getClass().getSimpleName());
        envelope.set("body", mapper.valueToTree(result));
        return mapper.writeValueAsString(envelope);
    }

    @Override
    public <T> T decode(String payload, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        JsonNode envelope = readEnvelope(payload);
        String storedType = envelope.path("type").asString("");
        if (!type.getSimpleName().equals(storedType)) {
            throw new IllegalArgumentException(
                    "idempotency payload holds " + storedType + " but " + type.getSimpleName() + " was requested");
        }
        return mapper.treeToValue(envelope.path("body"), type);
    }

    @Override
    public String encodeRejection(ReservationException rejection) {
        Objects.requireNonNull(rejection, "rejection must not be null");
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("v", VERSION);
        envelope.put("code", rejection.reservationCode().name());
        envelope.put("message", rejection.getMessage());
        envelope.set("details", mapper.valueToTree(rejection.details()));
        return mapper.writeValueAsString(envelope);
    }

    @Override
    public ReservationException decodeRejection(String payload) {
        JsonNode envelope = readEnvelope(payload);
        ReservationErrorCode code = ReservationErrorCode.valueOf(envelope.path("code").asString(""));
        String message = envelope.path("message").asString("");
        Map<String, Object> details = envelope.path("details").isObject()
                ? mapper.treeToValue(envelope.get("details"), DETAILS_TYPE)
                : new LinkedHashMap<>();
        restoreAlternativeNumbers(details);
        return ReservationException.replayed(code, message, details);
    }

    private JsonNode readEnvelope(String payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        JsonNode envelope = mapper.readTree(payload);
        int version = envelope.path("v").asInt(-1);
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported idempotency payload version: " + version);
        }
        return envelope;
    }

    /**
     * JSON no distingue {@code int} de {@code long}: devuelve a los tipos con que el dominio construye las
     * alternativas ({@code price} como long, {@code remainingCapacity} como int) para que el rechazo reproducido
     * sea idéntico al original.
     */
    private static void restoreAlternativeNumbers(Map<String, Object> details) {
        if (details.get("alternatives") instanceof List<?> alternatives) {
            for (Object item : alternatives) {
                if (item instanceof Map<?, ?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> alternative = (Map<String, Object>) raw;
                    if (alternative.get("price") instanceof Number price) {
                        alternative.put("price", price.longValue());
                    }
                    if (alternative.get("remainingCapacity") instanceof Number remaining) {
                        alternative.put("remainingCapacity", remaining.intValue());
                    }
                }
            }
        }
    }
}
