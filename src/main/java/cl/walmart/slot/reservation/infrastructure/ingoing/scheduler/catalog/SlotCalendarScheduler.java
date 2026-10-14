package cl.walmart.slot.reservation.infrastructure.ingoing.scheduler.catalog;

import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.usecase.catalog.SeedSoldOutDemoDayUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.CatalogZoneId;
import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Genera el horizonte al iniciar y diariamente; un fallo queda acotado al job. */
@Component
public class SlotCalendarScheduler {
    private static final Logger log = LoggerFactory.getLogger(SlotCalendarScheduler.class);

    private final GenerateSlotsUseCase generateSlots;
    private final SeedSoldOutDemoDayUseCase seedSoldOutDemoDay;
    private final CatalogProperties properties;
    private final MeterRegistry metrics;

    public SlotCalendarScheduler(GenerateSlotsUseCase generateSlots, SeedSoldOutDemoDayUseCase seedSoldOutDemoDay,
                                 CatalogProperties properties, MeterRegistry metrics) {
        this.generateSlots = generateSlots;
        this.seedSoldOutDemoDay = seedSoldOutDemoDay;
        this.properties = properties;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.generation().runOnStartup()) {
            runGeneration();
        }
    }

    @Scheduled(cron = "${app.catalog.generation.cron}", zone = "America/Santiago")
    public void runGeneration() {
        try {
            var report = generateSlots.generate();
            metrics.counter("catalog.generation.runs", "outcome", "success").increment();
            metrics.counter("catalog.slots.created").increment(report.created());
            log.info("Generación de catálogo completada: desde={}, hasta={}, creadas={}",
                    report.from(), report.to(), report.created());
        } catch (RuntimeException exception) {
            metrics.counter("catalog.generation.runs", "outcome", "failure").increment();
            log.error("Falló la generación programada del catálogo; las consultas siguen disponibles.", exception);
            return;
        }
        if (properties.demo().enabled()) {
            try {
                seedSoldOutDemoDay.seed(new CatalogZoneId(properties.demo().zoneId()), properties.demo().soldOutDayOffset());
            } catch (RuntimeException exception) {
                metrics.counter("catalog.demo.seed", "outcome", "failure").increment();
                log.error("Falló el día agotado demo del catálogo; la generación ya se completó.", exception);
            }
        }
    }
}
