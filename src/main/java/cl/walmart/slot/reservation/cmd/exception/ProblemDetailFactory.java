package cl.walmart.slot.reservation.cmd.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public interface ProblemDetailFactory {

    ProblemDetail create(HttpStatus status, String code, String detail, HttpServletRequest request);

    ProblemDetail create(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            Map<String, Object> extensions);
}
