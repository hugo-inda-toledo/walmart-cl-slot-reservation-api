package cl.walmart.slot.reservation.cmd.config.properties;

import cl.walmart.slot.reservation.cmd.config.AppConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppConfiguration.class)
            .withPropertyValues(
                    "app.cors.allowed-origins[0]=http://localhost:5173",
                    "app.problem.type-base-uri=https://problems.example.test/",
                    "app.correlation-id.max-length=64");

    @Test
    void bindsTheTypedApplicationPropertiesAndDefaultsTheExposedHeader() {
        contextRunner.run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);

            assertThat(properties.cors().allowedOrigins()).containsExactly("http://localhost:5173");
            assertThat(properties.cors().exposedHeaders()).containsExactly("X-Correlation-Id");
            assertThat(properties.problem().typeBaseUri().toString()).isEqualTo("https://problems.example.test/");
            assertThat(properties.correlationId().maxLength()).isEqualTo(64);
        });
    }
}
