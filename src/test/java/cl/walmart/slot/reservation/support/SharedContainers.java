package cl.walmart.slot.reservation.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public interface SharedContainers {

    /**
     * Cada contexto Spring cacheado conserva su pool de conexiones, y si un contexto no logra arrancar, al
     * destruirse cierra este contenedor compartido. Se sube {@code max_connections} para que la cantidad de
     * contextos en caché no agote el servidor según el orden de ejecución ({@code fsync=off} es el valor por
     * defecto de Testcontainers).
     */
    @ServiceConnection
    PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(ContainerImages.POSTGIS).asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=400");

    @ServiceConnection(name = "redis")
    GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse(ContainerImages.VALKEY))
            .withExposedPorts(6379);
}
