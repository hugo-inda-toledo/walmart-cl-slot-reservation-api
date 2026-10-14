package cl.walmart.slot.reservation.cmd.config;

import cl.walmart.slot.reservation.cmd.config.properties.ReservationProperties;
import cl.walmart.slot.reservation.application.service.reservation.ReservationSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Traduce {@code app.reservation} a {@link ReservationSettings}. Redeclara {@code @EnableScheduling} (inocuo si
 * catalog ya lo habilitó) para que los jobs de reservas no dependan de la configuración de otro contexto.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReservationProperties.class)
@EnableScheduling
public class ReservationConfiguration {

    @Bean
    ReservationSettings reservationSettings(ReservationProperties properties) {
        return new ReservationSettings(
                properties.holdTtl(),
                properties.alternatives().max(),
                properties.idempotency().retention(),
                properties.idempotency().waitTimeout(),
                properties.idempotency().retryAfterSeconds(),
                properties.sweeper().batchSize(),
                properties.sweeper().maxBatchesPerRun(),
                properties.idempotency().purgeBatchSize());
    }
}
