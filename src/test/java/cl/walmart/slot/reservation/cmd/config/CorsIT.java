package cl.walmart.slot.reservation.cmd.config;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CorsIT {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String REJECTED_ORIGIN = "http://evil.example";
    private static final String CORRELATION_ID = "cors-it";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void allowsConfiguredOriginForApiPreflightAndExposesCorrelationId() {
        ExchangeResult preflight = client().options().uri("/api/reservations")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Content-Type, Idempotency-Key, X-Correlation-Id, X-Customer-Id")
                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                .exchange().returnResult();

        assertThat(preflight.getStatus().is2xxSuccessful()).isTrue();
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("Content-Type")
                .containsIgnoringCase("Idempotency-Key")
                .containsIgnoringCase("X-Correlation-Id")
                .containsIgnoringCase("X-Customer-Id");
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE)).isEqualTo("3600");
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
        assertThat(preflight.getResponseHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo(CORRELATION_ID);

        ExchangeResult actualRequest = client().get().uri("/api/reservations")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                .exchange().returnResult();

        assertThat(actualRequest.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .containsIgnoringCase(CorrelationIdFilter.HEADER);
    }

    @Test
    void rejectsUnconfiguredOriginWithProblemDetailsAndNoCorsAuthorizationHeaders() throws Exception {
        ExchangeResult result = client().options().uri("/api/reservations")
                .header(HttpHeaders.ORIGIN, REJECTED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                .exchange().returnResult();

        assertThat(result.getStatus().value()).isEqualTo(403);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDto problem = objectMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(problem.getCode()).isEqualTo("CORS_REJECTED");
        assertThat(problem.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.getResponseHeaders().getFirst(CorrelationIdFilter.HEADER)).isEqualTo(CORRELATION_ID);
        assertThat(result.getResponseHeaders().headerNames())
                .noneMatch(header -> header.regionMatches(true, 0, "Access-Control-Allow-", 0, "Access-Control-Allow-".length()));
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
