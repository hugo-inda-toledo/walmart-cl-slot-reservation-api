package cl.walmart.slot.reservation.cmd.config.properties;

import cl.walmart.slot.reservation.cmd.config.ReservationConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

class ReservationPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReservationConfiguration.class);

    @Test
    void bindsDefaultsAndExposesTheApplicationSettings() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ReservationProperties properties = context.getBean(ReservationProperties.class);
            assertThat(properties.holdTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.alternatives().max()).isEqualTo(3);
            assertThat(properties.sweeper().enabled()).isTrue();
            assertThat(properties.sweeper().fixedDelay()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.sweeper().batchSize()).isEqualTo(500);
            assertThat(properties.sweeper().maxBatchesPerRun()).isEqualTo(20);
            assertThat(properties.idempotency().retention()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.idempotency().waitTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.idempotency().retryAfterSeconds()).isEqualTo(1);
            assertThat(properties.idempotency().purgeEnabled()).isTrue();
            assertThat(properties.idempotency().purgeCron()).isEqualTo("0 30 * * * *");
            assertThat(properties.idempotency().purgeBatchSize()).isEqualTo(1000);
            assertThat(context.getBean(ReservationSettings.class)).isEqualTo(new ReservationSettings(
                    Duration.ofMinutes(10), 3, Duration.ofHours(24), Duration.ofSeconds(5), 1, 500, 20, 1000));
        });
    }

    @Test
    void bindsOverridesIntoTheApplicationSettings() {
        contextRunner.withPropertyValues(
                        "app.reservation.hold-ttl=PT1S",
                        "app.reservation.alternatives.max=0",
                        "app.reservation.sweeper.enabled=false",
                        "app.reservation.sweeper.fixed-delay=PT10M",
                        "app.reservation.sweeper.batch-size=5000",
                        "app.reservation.sweeper.max-batches-per-run=100",
                        "app.reservation.idempotency.retention=P7D",
                        "app.reservation.idempotency.wait-timeout=PT0.1S",
                        "app.reservation.idempotency.retry-after-seconds=60",
                        "app.reservation.idempotency.purge-enabled=false",
                        "app.reservation.idempotency.purge-cron=0 0 * * * *",
                        "app.reservation.idempotency.purge-batch-size=10000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ReservationProperties.class).sweeper().enabled()).isFalse();
                    assertThat(context.getBean(ReservationProperties.class).idempotency().purgeEnabled()).isFalse();
                    assertThat(context.getBean(ReservationSettings.class)).isEqualTo(new ReservationSettings(
                            Duration.ofSeconds(1), 0, Duration.ofDays(7), Duration.ofMillis(100), 60, 5000, 100,
                            10000));
                });
    }

    @Test
    void rejectsValuesOutsideTheConfiguredRangesAtStartup() {
        assertThatContextFails("app.reservation.hold-ttl=PT0.5S");
        assertThatContextFails("app.reservation.hold-ttl=PT61M");
        assertThatContextFails("app.reservation.alternatives.max=-1");
        assertThatContextFails("app.reservation.alternatives.max=11");
        assertThatContextFails("app.reservation.sweeper.fixed-delay=PT0.5S");
        assertThatContextFails("app.reservation.sweeper.fixed-delay=PT11M");
        assertThatContextFails("app.reservation.sweeper.batch-size=0");
        assertThatContextFails("app.reservation.sweeper.batch-size=5001");
        assertThatContextFails("app.reservation.sweeper.max-batches-per-run=0");
        assertThatContextFails("app.reservation.sweeper.max-batches-per-run=101");
        assertThatContextFails("app.reservation.idempotency.retention=PT59S");
        assertThatContextFails("app.reservation.idempotency.retention=P8D");
        assertThatContextFails("app.reservation.idempotency.wait-timeout=PT0.09S");
        assertThatContextFails("app.reservation.idempotency.wait-timeout=PT31S");
        assertThatContextFails("app.reservation.idempotency.retry-after-seconds=0");
        assertThatContextFails("app.reservation.idempotency.retry-after-seconds=61");
        assertThatContextFails("app.reservation.idempotency.purge-cron= ");
        assertThatContextFails("app.reservation.idempotency.purge-cron=every hour");
        assertThatContextFails("app.reservation.idempotency.purge-batch-size=0");
        assertThatContextFails("app.reservation.idempotency.purge-batch-size=10001");
    }

    @Test
    void declaresTheReservationDefaultsAndExposedHeadersInTheBaseConfiguration() throws Exception {
        Map<?, ?> app = (Map<?, ?>) load("/application.yml").get("app");

        assertThat((List<Object>) ((Map<?, ?>) app.get("cors")).get("exposed-headers"))
                .containsExactly("X-Correlation-Id", "Retry-After", "Location");
        Map<?, ?> reservation = (Map<?, ?>) app.get("reservation");
        assertThat(reservation.get("hold-ttl")).isEqualTo("PT10M");
        assertThat(((Map<?, ?>) reservation.get("sweeper")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) reservation.get("idempotency")).get("purge-enabled")).isEqualTo(true);
    }

    @Test
    void disablesTheScheduledSweeperAndPurgeInTheTestProfile() throws Exception {
        Map<?, ?> reservation = (Map<?, ?>) ((Map<?, ?>) load("/application-test.yml").get("app"))
                .get("reservation");

        assertThat(((Map<?, ?>) reservation.get("sweeper")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) reservation.get("idempotency")).get("purge-enabled")).isEqualTo(false);
    }

    private void assertThatContextFails(String property) {
        contextRunner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    private Map<?, ?> load(String resource) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            return new Yaml().load(stream);
        }
    }
}
