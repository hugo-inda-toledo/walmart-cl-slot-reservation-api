package cl.walmart.slot.reservation.cmd.config;

import cl.walmart.slot.reservation.cmd.config.properties.CatalogProperties;
import cl.walmart.slot.reservation.application.service.catalog.CatalogSettings;
import cl.walmart.slot.reservation.domain.service.catalog.CutoffPolicy;
import cl.walmart.slot.reservation.domain.service.catalog.FixedLeadTimeCutoffPolicy;
import cl.walmart.slot.reservation.domain.service.catalog.PricingPolicy;
import cl.walmart.slot.reservation.domain.service.catalog.TemplatePricingPolicyImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogConfiguration {

    @Bean
    CatalogSettings catalogSettings(CatalogProperties properties) {
        return new CatalogSettings(properties.displayDays(), properties.generation().horizonDays());
    }

    @Bean
    CutoffPolicy cutoffPolicy(CatalogProperties properties) {
        return new FixedLeadTimeCutoffPolicy(properties.cutoffLeadTime());
    }

    @Bean
    PricingPolicy pricingPolicy() {
        return new TemplatePricingPolicyImpl();
    }
}
