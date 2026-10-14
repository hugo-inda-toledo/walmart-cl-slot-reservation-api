package cl.walmart.slot.reservation.cmd.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import cl.walmart.slot.reservation.domain.exception.DomainException;
import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import cl.walmart.slot.reservation.domain.exception.ErrorCode;
import cl.walmart.slot.reservation.infrastructure.ingoing.openapi.model.ProblemDto;
import cl.walmart.slot.reservation.support.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@Import(GlobalExceptionHandlerIT.ErrorFixtureConfiguration.class)
class GlobalExceptionHandlerIT {

    private static final String CORRELATION_ID = "exception-handler-it";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void mapsMvcAndDomainFailuresToContractProblemDetails() throws Exception {
        assertProblem(post("/test-errors/json", "{not-json"), 400, "MALFORMED_REQUEST");
        assertValidationProblem(post("/test-errors/validation", "{}"), "name");
        assertValidationProblem(get("/test-errors/parameter?quantity=wrong"), "quantity");
        assertValidationProblem(get("/test-errors/method-validation?quantity=0"), "quantity");
        assertValidationProblem(get("/test-errors/header"), "X-Required-Header");
        assertProblem(get("/test-errors/missing"), 404, "RESOURCE_NOT_FOUND");
        assertProblem(get("/test-errors/no-handler"), 404, "RESOURCE_NOT_FOUND");

        ExchangeResult methodNotAllowed = post("/test-errors/get-only", "{}");
        assertProblem(methodNotAllowed, 405, "METHOD_NOT_ALLOWED");
        assertThat(methodNotAllowed.getResponseHeaders().getFirst("Allow")).contains("GET");

        assertProblem(postWithContentType("/test-errors/json", "{}", MediaType.TEXT_PLAIN), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertProblem(getAccepting("/test-errors/get-only", MediaType.APPLICATION_XML), 406, "NOT_ACCEPTABLE");

        ExchangeResult domain = get("/test-errors/domain-conflict");
        ProblemDto domainProblem = assertProblem(domain, 409, "FIXTURE_CONFLICT");
        assertThat(domainProblem.getAdditionalProperty("reservationId")).isEqualTo("fixture-42");

        ExchangeResult unexpected = get("/test-errors/unexpected");
        ProblemDto unexpectedProblem = assertProblem(unexpected, 500, "INTERNAL_ERROR");
        String body = new String(unexpected.getResponseBodyContent(), StandardCharsets.UTF_8);
        assertThat(unexpectedProblem.getDetail()).isEqualTo("Ocurrió un error inesperado.");
        assertThat(body).doesNotContain("IllegalStateException", "at cl.", "select ");
    }

    @Test
    void mapsPreDispatcherFailuresThroughTheProblemErrorControllerWithCorrelationIdInTheErrorLog() throws Exception {
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> errorLogs = attachErrorLogCapture();
        try {
            ExchangeResult result = get("/test-errors/filter-before-dispatch");

            ProblemDto problem = assertProblem(result, 500, "INTERNAL_ERROR");
            assertThat(problem.getDetail()).isEqualTo("Ocurrió un error inesperado.");
            assertThat(errorLogs.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", CORRELATION_ID);
                    });
        } finally {
            errorLogs.stop();
            ((Logger) LoggerFactory.getLogger("cl.walmart.slot.reservation.cmd.exception.ProblemErrorController"))
                    .detachAppender(errorLogs);
        }
    }

    private static ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> attachErrorLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                "cl.walmart.slot.reservation.cmd.exception.ProblemErrorController");
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private ProblemDto assertProblem(ExchangeResult result, int status, String code) throws Exception {
        assertThat(result.getStatus().value()).isEqualTo(status);
        assertThat(result.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDto problem = objectMapper.readValue(result.getResponseBodyContent(), ProblemDto.class);
        assertThat(problem.getCode()).isEqualTo(code);
        assertThat(problem.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.getResponseHeaders().getFirst("X-Correlation-Id")).isEqualTo(CORRELATION_ID);
        return problem;
    }

    private void assertValidationProblem(ExchangeResult result, String field) throws Exception {
        ProblemDto problem = assertProblem(result, 400, "VALIDATION_ERROR");
        assertThat(problem.getErrors()).anySatisfy(error -> assertThat(error.getField()).isEqualTo(field));
    }

    private ExchangeResult get(String path) {
        return client().get().uri(path).header("X-Correlation-Id", CORRELATION_ID).exchange().returnResult();
    }

    private ExchangeResult getAccepting(String path, MediaType mediaType) {
        return client().get().uri(path).accept(mediaType).header("X-Correlation-Id", CORRELATION_ID).exchange().returnResult();
    }

    private ExchangeResult post(String path, String body) {
        return postWithContentType(path, body, MediaType.APPLICATION_JSON);
    }

    private ExchangeResult postWithContentType(String path, String body, MediaType mediaType) {
        return client().post().uri(path).contentType(mediaType).header("X-Correlation-Id", CORRELATION_ID)
                .body(body).exchange().returnResult();
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorFixtureConfiguration {

        @Bean
        FilterRegistrationBean<Filter> preDispatcherFailureFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                if ("/test-errors/filter-before-dispatch".equals(((HttpServletRequest) request).getRequestURI())) {
                    throw new IllegalStateException("fixture filter failure");
                }
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/test-errors/filter-before-dispatch");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            return registration;
        }

        @RestController
        static class ErrorFixtureController {

            @PostMapping(path = "/test-errors/json", consumes = MediaType.APPLICATION_JSON_VALUE)
            void json(@RequestBody Map<String, Object> ignored) {}

            @PostMapping(path = "/test-errors/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
            void validation(@Valid @RequestBody FixtureRequest request) {}

            @GetMapping("/test-errors/parameter")
            void parameter(@RequestParam int quantity) {}

            @GetMapping("/test-errors/header")
            void header(@RequestHeader("X-Required-Header") String header) {}

            @GetMapping("/test-errors/method-validation")
            void methodValidation(@RequestParam @Min(1) int quantity) {}

            @GetMapping("/test-errors/no-handler")
            void noHandler() throws NoHandlerFoundException {
                throw new NoHandlerFoundException("GET", "/test-errors/virtual", new org.springframework.http.HttpHeaders());
            }

            @GetMapping(path = "/test-errors/get-only", produces = MediaType.APPLICATION_JSON_VALUE)
            Map<String, String> getOnly() {
                return Map.of("status", "ok");
            }

            @GetMapping("/test-errors/domain-conflict")
            void domainConflict() {
                throw new FixtureConflictException();
            }

            @GetMapping("/test-errors/unexpected")
            void unexpected() {
                throw new IllegalStateException("select * from confidential_table");
            }
        }
    }

    record FixtureRequest(@NotBlank String name) {}

    private static final class FixtureConflictException extends DomainException {
        private FixtureConflictException() {
            super(() -> "FIXTURE_CONFLICT", ErrorCategory.CONFLICT, "Conflicto de fixture", Map.of("reservationId", "fixture-42"));
        }
    }
}
