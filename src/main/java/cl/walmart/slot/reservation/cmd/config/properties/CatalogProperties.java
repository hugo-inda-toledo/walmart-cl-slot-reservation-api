package cl.walmart.slot.reservation.cmd.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.UUID;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propiedades del catálogo bajo {@code app.catalog}. */
@Validated
@ConfigurationProperties("app.catalog")
public record CatalogProperties(
        @DefaultValue("7") @Min(1) @Max(14) int displayDays,
        @Valid @DefaultValue Generation generation,
        @DefaultValue("PT2H") @DurationMin(seconds = 0) @DurationMax(hours = 24) Duration cutoffLeadTime,
        @Valid @DefaultValue AvailabilityCache availabilityCache,
        @Valid @DefaultValue Demo demo) {

    @AssertTrue(message = "display-days no puede superar generation.horizon-days")
    public boolean isDisplayDaysWithinGenerationHorizon() {
        return displayDays <= generation.horizonDays;
    }

    public record Generation(
            @DefaultValue("14") @Min(1) @Max(60) int horizonDays,
            @DefaultValue("0 15 0 * * *") @NotBlank String cron,
            @DefaultValue("true") boolean runOnStartup) {
    }

    public record AvailabilityCache(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT30S") @DurationMin(seconds = 1) @DurationMax(minutes = 5) Duration ttl,
            @DefaultValue("PT5S") @DurationMin(seconds = 0) @DurationMax(minutes = 1) Duration failureBackoff) {
    }

    public record Demo(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("5f1c0e2a-0000-4000-8000-000000000401") UUID zoneId,
            @DefaultValue("2") @Min(0) @Max(13) int soldOutDayOffset) {
    }
}
