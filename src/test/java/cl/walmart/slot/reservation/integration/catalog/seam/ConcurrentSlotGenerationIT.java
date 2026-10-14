package cl.walmart.slot.reservation.integration.catalog.seam;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.usecase.catalog.GenerateSlotsUseCase;
import cl.walmart.slot.reservation.domain.entities.catalog.GenerationReport;
import cl.walmart.slot.reservation.support.IntegrationTest;
import cl.walmart.slot.reservation.support.MutableClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Generación concurrente (requisitos 2.5, 12.4): N hilos ejecutan la generación completa a la vez con reloj fijo
 * y el resultado es una ventana por plantilla y fecha, con un reporte total de creadas igual al esperado.
 */
@IntegrationTest
@Import(SeamClockConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrentSlotGenerationIT {

    private static final int THREADS = 8;
    private static final LocalDate FROM = LocalDate.of(2037, 3, 5);
    private static final LocalDate TO = FROM.plusDays(13);

    @Autowired
    private GenerateSlotsUseCase generation;
    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void startFromAnEmptyCalendar() {
        clock.setInstant(SeamClockConfiguration.TODAY_MORNING);
        clear();
    }

    @AfterAll
    void clear() {
        jdbc.update("delete from catalog_delivery_slot where delivery_date >= date '2037-01-01'");
    }

    @Test
    void createsOneWindowPerTemplateAndDateAndReportsTheExpectedTotal() throws Exception {
        int expected = jdbc.queryForObject("""
                select count(*) from catalog_slot_template t
                join geo_delivery_zone z on z.id = t.zone_id
                cross join generate_series(?::date, ?::date, interval '1 day') d
                where t.status = 'ACTIVE' and z.status = 'ACTIVE'
                  and extract(isodow from d) = t.day_of_week
                  and not exists (select 1 from catalog_holiday h
                                  where h.holiday_date = d::date and (h.zone_id is null or h.zone_id = t.zone_id))
                """, Integer.class, FROM, TO);
        assertThat(expected).isPositive();

        List<GenerationReport> reports = runConcurrently();

        assertThat(reports).hasSize(THREADS);
        assertThat(reports).extracting(GenerationReport::from).containsOnly(FROM);
        assertThat(reports).extracting(GenerationReport::to).containsOnly(TO);
        assertThat(reports.stream().mapToInt(GenerationReport::created).sum()).isEqualTo(expected);
        assertThat(count("select count(*) from catalog_delivery_slot where delivery_date between ? and ?"))
                .isEqualTo(expected);
        assertThat(count("""
                select count(*) from (select template_id, delivery_date from catalog_delivery_slot
                where delivery_date between ? and ? group by 1, 2 having count(*) > 1) duplicates"""))
                .isZero();
        assertThat(runConcurrently().stream().mapToInt(GenerationReport::created).sum())
                .as("repetir la generación no crea nada").isZero();
    }

    private List<GenerationReport> runConcurrently() throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            List<Future<GenerationReport>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<GenerationReport> task = () -> {
                    ready.countDown();
                    go.await();
                    return generation.generate();
                };
                futures.add(executor.submit(task));
            }
            ready.await();
            go.countDown();
            List<GenerationReport> reports = new ArrayList<>();
            for (Future<GenerationReport> future : futures) {
                reports.add(future.get());
            }
            return reports;
        }
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, FROM, TO);
    }
}
