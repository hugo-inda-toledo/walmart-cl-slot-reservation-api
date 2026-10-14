package cl.walmart.slot.reservation.infrastructure.ingoing.controller.reservation;

import cl.walmart.slot.reservation.domain.exception.reservation.IdempotencyInProgressException;
import cl.walmart.slot.reservation.cmd.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Responde 409 con {@code Retry-After} cuando la clave de idempotencia sigue en curso. Solo maneja esa excepción;
 * el resto de los errores sigue en el manejador global.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ReservationProblemAdvice {

    private static final Logger log = LoggerFactory.getLogger(ReservationProblemAdvice.class);

    private final ProblemDetailFactory problemDetailFactory;

    public ReservationProblemAdvice(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    ResponseEntity<ProblemDetail> handleIdempotencyInProgress(
            IdempotencyInProgressException exception, HttpServletRequest request) {
        log.info("Domain error: code={}, category={}", exception.code().value(), exception.category());
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.CONFLICT, exception.code().value(), exception.getMessage(), request, exception.details());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(HttpHeaders.RETRY_AFTER, Integer.toString(exception.retryAfterSeconds()));
        return new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
    }
}
