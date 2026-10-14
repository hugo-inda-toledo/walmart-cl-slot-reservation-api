package cl.walmart.slot.reservation.cmd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita la ejecución programada del calendario del catálogo. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class CatalogSchedulingConfiguration {
}
