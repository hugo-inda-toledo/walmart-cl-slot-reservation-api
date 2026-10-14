package cl.walmart.slot.reservation.cmd.config;

import cl.walmart.slot.reservation.cmd.exception.PlatformErrorCode;
import cl.walmart.slot.reservation.cmd.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import tools.jackson.databind.json.JsonMapper;

/** Writes RFC 9457 responses when a CORS request is rejected before MVC dispatch. */
public final class ProblemCorsProcessor extends DefaultCorsProcessor {

    private final ProblemDetailFactory problemDetailFactory;
    private final JsonMapper jsonMapper;
    private final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

    public ProblemCorsProcessor(ProblemDetailFactory problemDetailFactory, JsonMapper jsonMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean processRequest(
            CorsConfiguration configuration, HttpServletRequest request, HttpServletResponse response) throws IOException {
        currentRequest.set(request);
        try {
            return super.processRequest(configuration, request, response);
        } finally {
            currentRequest.remove();
        }
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) throws IOException {
        HttpServletRequest request = currentRequest.get();
        if (request == null) {
            throw new IllegalStateException("A servlet request is required to create a CORS problem detail");
        }

        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        jsonMapper.writeValue(response.getBody(), problemDetailFactory.create(
                HttpStatus.FORBIDDEN,
                PlatformErrorCode.CORS_REJECTED.value(),
                "El origen de la solicitud no está permitido.",
                request));
        response.flush();
    }
}
