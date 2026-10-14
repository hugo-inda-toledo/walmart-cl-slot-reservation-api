package cl.walmart.slot.reservation.cmd.exception;

import cl.walmart.slot.reservation.cmd.config.CorrelationIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Produces the API problem contract for failures dispatched to Boot's {@code /error} endpoint. */
@RestController
public final class ProblemErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);
    private static final String UNEXPECTED_ERROR_DETAIL = "Ocurrió un error inesperado.";

    private final ProblemDetailFactory problemDetailFactory;

    public ProblemErrorController(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @RequestMapping(path = "${server.error.path:${error.path:/error}}", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        String correlationId = correlationId(request);
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            response.setHeader(CorrelationIdFilter.HEADER, correlationId);
        }

        try {
            HttpStatus status = status(request);
            PlatformErrorCode code = code(status);
            logError(request, status);
            ProblemDetail problem = problemDetailFactory.create(status, code.value(), detail(status), request, Map.of());
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static HttpStatus status(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static PlatformErrorCode code(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return PlatformErrorCode.RESOURCE_NOT_FOUND;
        }
        if (status.is4xxClientError()) {
            return PlatformErrorCode.MALFORMED_REQUEST;
        }
        return PlatformErrorCode.INTERNAL_ERROR;
    }

    private static String detail(HttpStatus status) {
        return status.is5xxServerError() ? UNEXPECTED_ERROR_DETAIL : "La solicitud no pudo ser procesada.";
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String correlationId ? correlationId : null;
    }

    private static void logError(HttpServletRequest request, HttpStatus status) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (exception instanceof Throwable throwable) {
            log.error("Unhandled request error outside DispatcherServlet: status={}", status.value(), throwable);
        } else {
            log.error("Unhandled request error outside DispatcherServlet: status={}", status.value());
        }
    }
}
