package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;

import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.GenerationReport;
import cl.walmart.slot.reservation.domain.usecase.catalog.SeedSoldOutDemoDayUseCase;
import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlotCalendarSchedulerTest {

    @Test
    void capturesGenerationFailuresWithoutCallingDemo() {
        var demoCalls = new int[1];
        var scheduler = new SlotCalendarScheduler(() -> { throw new IllegalStateException("database unavailable"); },
                (zone, offset) -> { demoCalls[0]++; return java.util.Optional.empty(); }, properties(true), new SimpleMeterRegistry());

        assertThatCode(scheduler::runGeneration).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(demoCalls[0]).isZero();
    }

    @Test
    void invokesDemoOnlyWhenEnabledAfterSuccessfulGeneration() {
        var demoCalls = new int[1];
        GenerateSlotsUseCase generation = () -> new GenerationReport(LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 1, 14), 3, Set.of());
        SeedSoldOutDemoDayUseCase demo = (zone, offset) -> { demoCalls[0]++; return java.util.Optional.empty(); };

        new SlotCalendarScheduler(generation, demo, properties(true), new SimpleMeterRegistry()).runGeneration();
        new SlotCalendarScheduler(generation, demo, properties(false), new SimpleMeterRegistry()).runGeneration();

        org.assertj.core.api.Assertions.assertThat(demoCalls[0]).isEqualTo(1);
    }

    private CatalogProperties properties(boolean demoEnabled) {
        return new CatalogProperties(7, new CatalogProperties.Generation(14, "0 0 0 * * *", true),
                java.time.Duration.ofHours(2), new CatalogProperties.AvailabilityCache(true, java.time.Duration.ofSeconds(30),
                java.time.Duration.ofSeconds(5)), new CatalogProperties.Demo(demoEnabled, UUID.randomUUID(), 2));
    }
}
