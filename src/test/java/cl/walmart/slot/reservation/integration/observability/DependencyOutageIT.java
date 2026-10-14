package cl.walmart.slot.reservation.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cl.walmart.slot.reservation.support.ContainerImages;
import java.time.Duration;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(OrderAnnotation.class)
class DependencyOutageIT {

    private static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"));
    private static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse(ContainerImages.VALKEY))
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void configureDependencies(DynamicPropertyRegistry registry) {
        POSTGIS.start();
        VALKEY.start();

        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", VALKEY::getFirstMappedPort);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
        registry.add("spring.datasource.hikari.validation-timeout", () -> "1000");
    }

    @Test
    @Order(1)
    void keepsReadinessUpWhileValkeyIsDownAndReportsRedisDown() {
        VALKEY.stop();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(readiness().getStatus().value()).isEqualTo(200);
            assertThat(body(aggregateHealth())).containsPattern("(?s)\"redis\":\\{.*?\"status\":\"DOWN\"");
        });
    }

    @Test
    @Order(2)
    void reportsReadinessDownWhenPostgisIsDown() {
        POSTGIS.stop();

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(readiness().getStatus().value()).isEqualTo(503));
    }

    private ExchangeResult readiness() {
        return client().get().uri("/actuator/health/readiness").exchange().returnResult();
    }

    private ExchangeResult aggregateHealth() {
        return client().get().uri("/actuator/health").exchange().returnResult();
    }

    private String body(ExchangeResult result) {
        return new String(result.getResponseBodyContent());
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
