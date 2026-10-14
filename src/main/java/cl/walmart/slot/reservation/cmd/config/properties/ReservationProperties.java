package cl.walmart.slot.reservation.cmd.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/** Propiedades de reservas bajo {@code app.reservation}. */
@Validated
@ConfigurationProperties("app.reservation")
public record ReservationProperties(
        @DefaultValue("PT10M") @DurationMin(seconds = 1) @DurationMax(hours = 1) Duration holdTtl,
        @Valid @DefaultValue Alternatives alternatives,
        @Valid @DefaultValue Sweeper sweeper,
        @Valid @DefaultValue Idempotency idempotency) {

    /** Sugerencias de ventanas ante {@code SLOT_FULL}; {@code max = 0} las desactiva. */
    public record Alternatives(@DefaultValue("3") @Min(0) @Max(10) int max) {
    }

    /** Liberación automática de retenciones vencidas. */
    public record Sweeper(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT15S") @DurationMin(seconds = 1) @DurationMax(minutes = 10) Duration fixedDelay,
            @DefaultValue("500") @Min(1) @Max(5000) int batchSize,
            @DefaultValue("20") @Min(1) @Max(100) int maxBatchesPerRun) {
    }

    /** Retención, espera acotada y purga de claves de idempotencia. */
    public record Idempotency(
            @DefaultValue("PT24H") @DurationMin(minutes = 1) @DurationMax(days = 7) Duration retention,
            @DefaultValue("PT5S") @DurationMin(millis = 100) @DurationMax(seconds = 30) Duration waitTimeout,
            @DefaultValue("1") @Min(1) @Max(60) int retryAfterSeconds,
            @DefaultValue("true") boolean purgeEnabled,
            @DefaultValue("0 30 * * * *") @NotBlank String purgeCron,
            @DefaultValue("1000") @Min(1) @Max(10000) int purgeBatchSize) {

        @AssertTrue(message = "purge-cron debe ser una expresión cron válida de Spring")
        public boolean isPurgeCronValid() {
            return purgeCron != null && CronExpression.isValidExpression(purgeCron);
        }
    }
}
