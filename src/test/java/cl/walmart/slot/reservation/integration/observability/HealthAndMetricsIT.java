package cl.walmart.slot.reservation.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class HealthAndMetricsIT {

    @LocalServerPort
    private int port;

    @Test
    void exposesUnauthenticatedLivenessAndReadinessAsUp() {
        assertThat(client().get().uri("/actuator/health/liveness").exchange().returnResult().getStatus().value())
                .isEqualTo(200);
        assertThat(client().get().uri("/actuator/health/readiness").exchange().returnResult().getStatus().value())
                .isEqualTo(200);
    }

    @Test
    void exposesTheRequiredMetrics() {
        client().get().uri("/unmapped-route-to-record-http-metrics").exchange();

        assertMetricExists("http.server.requests");
        assertMetricExists("hikaricp.connections.active");
        assertMetricExists("jvm.memory.used");
    }

    @Test
    void keepsSensitiveActuatorEndpointsUnexposed() {
        assertThat(client().get().uri("/actuator/env").exchange().returnResult().getStatus().value()).isEqualTo(404);
        assertThat(client().get().uri("/actuator/beans").exchange().returnResult().getStatus().value()).isEqualTo(404);
    }

    private void assertMetricExists(String metric) {
        ExchangeResult result = client().get().uri("/actuator/metrics/{metric}", metric).exchange().returnResult();
        assertThat(result.getStatus().value()).isEqualTo(200);
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
