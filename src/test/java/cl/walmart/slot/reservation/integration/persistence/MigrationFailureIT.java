package cl.walmart.slot.reservation.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.Application;
import cl.walmart.slot.reservation.support.ContainerImages;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class MigrationFailureIT {

    private static final String UNREACHABLE_JDBC_URL = "jdbc:postgresql://localhost:1/unreachable";
    private static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"));
    private static final PostgreSQLContainer<?> POSTGRES_WITHOUT_POSTGIS =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    private static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse(ContainerImages.VALKEY))
            .withExposedPorts(6379);

    @BeforeAll
    static void startDependencies() {
        POSTGIS.start();
        POSTGRES_WITHOUT_POSTGIS.start();
        VALKEY.start();
    }

    @AfterAll
    static void stopDependencies() {
        VALKEY.stop();
        POSTGRES_WITHOUT_POSTGIS.stop();
        POSTGIS.stop();
    }

    @Test
    void abortsStartupWhenTheDatabaseIsUnreachableAndReportsTheCredentialFreeJdbcUrl() {
        Throwable failure = startupFailure(propertiesFor(POSTGIS, Map.of("spring.datasource.url", UNREACHABLE_JDBC_URL)));

        assertThat(messageChain(failure)).contains(UNREACHABLE_JDBC_URL);
    }

    @Test
    void abortsStartupWhenAnAppliedMigrationChecksumChangesAndReportsItsVersion() {
        try (ConfigurableApplicationContext ignored = start(propertiesFor(POSTGIS, Map.of()))) {
            // Apply the real migration before validating the deliberately altered checksum fixture.
        }

        Throwable failure = startupFailure(propertiesFor(POSTGIS,
                Map.of("spring.flyway.locations", "classpath:db/migration-checksum")));

        assertThat(messageChain(failure)).contains("1.1");
    }

    @Test
    void abortsStartupWithoutPostgisAndReportsTheMissingExtension() {
        Throwable failure = startupFailure(propertiesFor(POSTGRES_WITHOUT_POSTGIS, Map.of()));

        assertThat(messageChain(failure)).containsIgnoringCase("postgis");
    }

    @Test
    void upgradesAnExistingV41SchemaWithoutOutOfOrderMigrations() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>(
                DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"))) {
            database.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("4.1")).outOfOrder(false).load().migrate();
            assertThat(jdbc.queryForObject(
                    "select version from flyway_schema_history order by installed_rank desc limit 1", String.class))
                    .isEqualTo("4.1");
            assertThat(jdbc.queryForObject("select to_regclass('geo_coverage_provenance')", String.class)).isNull();

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .outOfOrder(false).load().migrate();
            assertThat(jdbc.queryForObject(
                    "select count(*) from flyway_schema_history where version = '4.2' and success", Integer.class))
                    .isOne();
            assertThat(jdbc.queryForObject("select count(*) from geo_coverage_provenance", Integer.class))
                    .isEqualTo(153);
            assertThat(jdbc.queryForObject("select count(*) from catalog_slot_template t "
                    + "join geo_delivery_zone z on z.id = t.zone_id where z.code like 'LIDER_REGION_%'",
                    Integer.class)).isEqualTo(105);
        }
    }

    private ConfigurableApplicationContext start(Map<String, Object> properties) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("spring.main.web-application-type", "none");
        arguments.put("spring.profiles.active", "test");
        arguments.putAll(properties);
        return Application.run(arguments.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new));
    }

    private Throwable startupFailure(Map<String, Object> properties) {
        try (ConfigurableApplicationContext context = start(properties)) {
            throw new AssertionError("Expected application startup to fail, but it completed successfully");
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Map<String, Object> propertiesFor(PostgreSQLContainer<?> database, Map<String, Object> overrides) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", database.getJdbcUrl());
        properties.put("spring.datasource.username", database.getUsername());
        properties.put("spring.datasource.password", database.getPassword());
        properties.put("spring.data.redis.host", VALKEY.getHost());
        properties.put("spring.data.redis.port", VALKEY.getFirstMappedPort());
        properties.put("spring.datasource.hikari.connection-timeout", "1000");
        properties.put("spring.datasource.hikari.validation-timeout", "500");
        properties.put("spring.datasource.hikari.initialization-fail-timeout", "1000");
        properties.putAll(overrides);
        return properties;
    }

    private String messageChain(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}
