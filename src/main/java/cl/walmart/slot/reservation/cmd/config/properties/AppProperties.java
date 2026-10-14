package cl.walmart.slot.reservation.cmd.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @Valid @DefaultValue Cors cors,
        @Valid @DefaultValue Problem problem,
        @Valid @DefaultValue CorrelationId correlationId) {

    public record Cors(
            List<String> allowedOrigins,
            @DefaultValue("X-Correlation-Id") List<String> exposedHeaders) {
    }

    public record Problem(@DefaultValue("urn:slot-reservation:problem:") URI typeBaseUri) {
    }

    public record CorrelationId(@DefaultValue("64") @Min(1) @Max(64) int maxLength) {
    }
}
