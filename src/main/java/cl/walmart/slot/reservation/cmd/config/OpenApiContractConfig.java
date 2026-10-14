package cl.walmart.slot.reservation.cmd.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Exposes the versioned OpenAPI source of truth without deriving a contract from controllers. */
@Configuration
public class OpenApiContractConfig implements WebMvcConfigurer {

    private static final MediaType APPLICATION_YAML = MediaType.parseMediaType("application/yaml");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**")
                .addResourceLocations("classpath:/openapi/");
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("yml", APPLICATION_YAML);
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> yamlMimeType() {
        return factory -> factory.addContextCustomizers(
                context -> context.addMimeMapping("yml", APPLICATION_YAML.toString()));
    }
}
