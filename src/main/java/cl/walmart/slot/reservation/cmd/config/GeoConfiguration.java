package cl.walmart.slot.reservation.cmd.config;

import cl.walmart.slot.reservation.cmd.config.properties.GeoProperties;
import cl.walmart.slot.reservation.domain.entities.geo.BorderTolerance;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeoProperties.class)
public class GeoConfiguration {

    @Bean
    BorderTolerance borderTolerance(GeoProperties properties) {
        return new BorderTolerance(properties.borderToleranceMeters());
    }
}
