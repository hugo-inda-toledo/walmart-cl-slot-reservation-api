package cl.walmart.slot.reservation.integration.catalog.acceleration;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.ContainerImages;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Valkey detenido (contenedores propios de la clase, como {@code DependencyOutageIT}): la aplicación arranca con
 * la caché activa, Valkey se detiene y las respuestas siguen siendo idénticas porque PostgreSQL es la única
 * fuente de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AccelerationClockConfiguration.class)
class AvailabilityAccelerationValkeyDownIT extends AvailabilityAccelerationScenario {

    private static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"));
    private static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse(ContainerImages.VALKEY))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDependencies(DynamicPropertyRegistry registry) {
        POSTGIS.start();
        VALKEY.start();
        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", VALKEY::getFirstMappedPort);
    }

    @Test
    void producesTheSameResponsesWhileValkeyIsStopped() throws Exception {
        VALKEY.stop();

        List<String> observed = observeScenario();

        assertThat(observed).containsExactlyElementsOf(expectedObservations());
    }
}
