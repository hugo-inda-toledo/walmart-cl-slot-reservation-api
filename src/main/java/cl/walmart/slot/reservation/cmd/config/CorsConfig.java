package cl.walmart.slot.reservation.cmd.config;

import cl.walmart.slot.reservation.cmd.config.properties.AppProperties;
import cl.walmart.slot.reservation.cmd.exception.ProblemDetailFactory;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Content-Type", "Accept", "Idempotency-Key", "X-Correlation-Id", "X-Customer-Id");

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            AppProperties appProperties, ProblemDetailFactory problemDetailFactory, JsonMapper jsonMapper) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(appProperties.cors().allowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(appProperties.cors().exposedHeaders());
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        CorsFilter corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor(new ProblemCorsProcessor(problemDetailFactory, jsonMapper));

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
