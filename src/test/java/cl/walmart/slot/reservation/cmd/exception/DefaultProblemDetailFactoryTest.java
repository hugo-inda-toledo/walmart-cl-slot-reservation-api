package cl.walmart.slot.reservation.cmd.exception;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.cmd.config.properties.AppProperties;
import cl.walmart.slot.reservation.cmd.config.CorrelationIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class DefaultProblemDetailFactoryTest {

    private final ProblemDetailFactory factory = new DefaultProblemDetailFactory(properties());

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void createsAProblemWithThePlatformCodeHttpTitleRequestAttributeAndExtensions() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "mdc-correlation-id");
        var request = request("/api/v1/reservations", "draft=true");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "request-correlation-id");
        var extensions = Map.<String, Object>of("errors", List.of(Map.of("field", "slotId")), "retryable", false);

        var problem = factory.create(
                HttpStatus.BAD_REQUEST,
                PlatformErrorCode.VALIDATION_ERROR.value(),
                "Los datos no son válidos.",
                request,
                extensions);

        assertThat(problem.getType()).isEqualTo(URI.create("urn:slot-reservation:problem:validation-error"));
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Los datos no son válidos.");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/reservations"));
        assertThat(problem.getProperties())
                .containsEntry("code", "VALIDATION_ERROR")
                .containsEntry("correlationId", "request-correlation-id")
                .containsAllEntriesOf(extensions);
    }

    @Test
    void usesMdcCorrelationIdWhenTheRequestDoesNotHaveOne() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "mdc-correlation-id");

        var problem = factory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                PlatformErrorCode.INTERNAL_ERROR.value(),
                "Ocurrió un error inesperado.",
                request("/api/v1/reservations", null));

        assertThat(problem.getProperties()).containsEntry("correlationId", "mdc-correlation-id");
    }

    @Test
    void usesTheOriginalRequestUriForAnErrorDispatch() {
        var request = request("/error", null);
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/original?ignored=true");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "error-dispatch-id");

        var problem = factory.create(
                HttpStatus.NOT_FOUND,
                PlatformErrorCode.RESOURCE_NOT_FOUND.value(),
                "No encontrado.",
                request);

        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/original"));
    }

    @Test
    void exposesEveryPlatformCodeThroughTheStableErrorCodeContract() {
        assertThat(PlatformErrorCode.values()).extracting(PlatformErrorCode::value).containsExactly(
                "MALFORMED_REQUEST",
                "VALIDATION_ERROR",
                "RESOURCE_NOT_FOUND",
                "METHOD_NOT_ALLOWED",
                "NOT_ACCEPTABLE",
                "UNSUPPORTED_MEDIA_TYPE",
                "CORS_REJECTED",
                "INTERNAL_ERROR");
    }

    private static MockHttpServletRequest request(String requestUri, String queryString) {
        var request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        request.setQueryString(queryString);
        return request;
    }

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Cors(List.of(), List.of(CorrelationIdFilter.HEADER)),
                new AppProperties.Problem(URI.create("urn:slot-reservation:problem:")),
                new AppProperties.CorrelationId(64));
    }
}
