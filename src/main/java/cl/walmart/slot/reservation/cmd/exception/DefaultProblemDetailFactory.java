package cl.walmart.slot.reservation.cmd.exception;

import cl.walmart.slot.reservation.cmd.config.properties.AppProperties;
import cl.walmart.slot.reservation.cmd.config.CorrelationIdFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public final class DefaultProblemDetailFactory implements ProblemDetailFactory {

    private final AppProperties appProperties;

    public DefaultProblemDetailFactory(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public ProblemDetail create(HttpStatus status, String code, String detail, HttpServletRequest request) {
        return create(status, code, detail, request, Map.of());
    }

    @Override
    public ProblemDetail create(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, Object> extensions) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(problemType(code));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(withoutQueryString(requestUri(request))));
        extensions.forEach(problem::setProperty);
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private URI problemType(String code) {
        return URI.create(appProperties.problem().typeBaseUri() + kebabCase(code));
    }

    private static String kebabCase(String code) {
        return code.replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static String requestUri(HttpServletRequest request) {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            var originalRequestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (originalRequestUri instanceof String uri) {
                return uri;
            }
        }
        return request.getRequestURI();
    }

    private static String withoutQueryString(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(0, queryStart) : uri;
    }

    private static String correlationId(HttpServletRequest request) {
        var requestCorrelationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return requestCorrelationId instanceof String correlationId
                ? correlationId
                : MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
