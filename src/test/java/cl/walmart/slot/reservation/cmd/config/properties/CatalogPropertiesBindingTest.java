package cl.walmart.slot.reservation.cmd.config.properties;

import cl.walmart.slot.reservation.cmd.config.CatalogConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.service.catalog.CutoffPolicy;
import cl.walmart.slot.reservation.domain.service.catalog.PricingPolicy;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CatalogPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogConfiguration.class);

    @Test
    void bindsDefaultsAndExposesCatalogBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            CatalogProperties properties = context.getBean(CatalogProperties.class);
            assertThat(properties.displayDays()).isEqualTo(7);
            assertThat(properties.generation().horizonDays()).isEqualTo(14);
            assertThat(properties.generation().cron()).isEqualTo("0 15 0 * * *");
            assertThat(properties.generation().runOnStartup()).isTrue();
            assertThat(properties.cutoffLeadTime()).isEqualTo(Duration.ofHours(2));
            assertThat(properties.availabilityCache().enabled()).isTrue();
            assertThat(properties.availabilityCache().ttl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.availabilityCache().failureBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.demo().enabled()).isFalse();
            assertThat(properties.demo().zoneId())
                    .isEqualTo(UUID.fromString("5f1c0e2a-0000-4000-8000-000000000401"));
            assertThat(properties.demo().soldOutDayOffset()).isEqualTo(2);
            assertThat(context.getBean(CatalogSettings.class)).isEqualTo(new CatalogSettings(7, 14));
            assertThat(context).hasSingleBean(CutoffPolicy.class);
            assertThat(context).hasSingleBean(PricingPolicy.class);
        });
    }

    @Test
    void bindsOverridesIntoTheConfiguration() {
        contextRunner.withPropertyValues(
                        "app.catalog.display-days=10",
                        "app.catalog.generation.horizon-days=20",
                        "app.catalog.generation.cron=0 0 1 * * *",
                        "app.catalog.generation.run-on-startup=false",
                        "app.catalog.cutoff-lead-time=PT4H",
                        "app.catalog.availability-cache.enabled=false",
                        "app.catalog.availability-cache.ttl=PT1M",
                        "app.catalog.availability-cache.failure-backoff=PT10S",
                        "app.catalog.demo.enabled=true",
                        "app.catalog.demo.zone-id=00000000-0000-4000-8000-000000000123",
                        "app.catalog.demo.sold-out-day-offset=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CatalogSettings.class)).isEqualTo(new CatalogSettings(10, 20));
                    assertThat(context.getBean(CatalogProperties.class).demo().enabled()).isTrue();
                    assertThat(context.getBean(CatalogProperties.class).demo().soldOutDayOffset()).isEqualTo(5);
                });
    }

    @Test
    void rejectsValuesOutsideTheConfiguredRanges() {
        assertThatContextFails("app.catalog.display-days=0");
        assertThatContextFails("app.catalog.display-days=15");
        assertThatContextFails("app.catalog.generation.horizon-days=0");
        assertThatContextFails("app.catalog.generation.horizon-days=61");
        assertThatContextFails("app.catalog.cutoff-lead-time=PT25H");
        assertThatContextFails("app.catalog.availability-cache.ttl=PT0S");
        assertThatContextFails("app.catalog.availability-cache.failure-backoff=PT61S");
        assertThatContextFails("app.catalog.demo.sold-out-day-offset=14");
    }

    @Test
    void rejectsDisplayDaysGreaterThanTheGenerationHorizon() {
        contextRunner.withPropertyValues(
                        "app.catalog.display-days=8", "app.catalog.generation.horizon-days=7")
                .run(context -> assertThat(context).hasFailed());
    }

    private void assertThatContextFails(String property) {
        contextRunner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
}
