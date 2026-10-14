package cl.walmart.slot.reservation.cmd.exception;

import cl.walmart.slot.reservation.domain.exception.DomainException;
import cl.walmart.slot.reservation.domain.exception.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Translates MVC and domain failures to the API's RFC 9457 problem contract. */
@RestControllerAdvice
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.BAD_REQUEST, PlatformErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la solicitud no tiene un formato válido.", Map.of(), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationProblem(fieldErrors(exception.getBindingResult().getFieldErrors()), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors = exception.getParameterValidationResults().stream()
                .map(result -> fieldError(parameterName(result), "El valor no cumple las restricciones."))
                .toList();
        return validationProblem(errors, headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationProblem(List.of(fieldError(exception.getParameterName(), "El parámetro es obligatorio.")), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = exception instanceof MissingRequestHeaderException missingHeader
                ? missingHeader.getHeaderName()
                : "request";
        return validationProblem(List.of(fieldError(field, "El encabezado de la solicitud es obligatorio.")), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = exception.getPropertyName() == null ? "request" : exception.getPropertyName();
        return validationProblem(List.of(fieldError(field, "El valor tiene un formato inválido.")), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.METHOD_NOT_ALLOWED, PlatformErrorCode.METHOD_NOT_ALLOWED,
                "El método HTTP no está permitido para este recurso.", Map.of(), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, PlatformErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "El tipo de contenido de la solicitud no es compatible.", Map.of(), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            org.springframework.web.HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.NOT_ACCEPTABLE, PlatformErrorCode.NOT_ACCEPTABLE,
                "No existe una representación aceptable del recurso.", Map.of(), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.NOT_FOUND, PlatformErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no fue encontrado.", Map.of(), headers, request, exception);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return frameworkProblem(HttpStatus.NOT_FOUND, PlatformErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no fue encontrado.", Map.of(), headers, request, exception);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> fieldError(violation.getPropertyPath().toString(), "El valor no cumple las restricciones."))
                .toList();
        log.warn("Framework request error: code={}, status={}", PlatformErrorCode.VALIDATION_ERROR.value(), 400);
        return response(HttpStatus.BAD_REQUEST, PlatformErrorCode.VALIDATION_ERROR.value(),
                "La solicitud contiene errores de validación.", Map.of("errors", errors), new HttpHeaders(), request);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomainException(DomainException exception, HttpServletRequest request) {
        HttpStatus status = domainStatus(exception.category());
        log.info("Domain error: code={}, category={}", exception.code().value(), exception.category());
        return response(status, exception.code().value(), exception.getMessage(), exception.details(), new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, PlatformErrorCode.INTERNAL_ERROR.value(),
                "Ocurrió un error inesperado.", Map.of(), new HttpHeaders(), request);
    }

    private ResponseEntity<Object> validationProblem(
            List<Map<String, String>> errors, HttpHeaders headers, WebRequest request, Exception exception) {
        return frameworkProblem(HttpStatus.BAD_REQUEST, PlatformErrorCode.VALIDATION_ERROR,
                "La solicitud contiene errores de validación.", Map.of("errors", errors), headers, request, exception);
    }

    private ResponseEntity<Object> frameworkProblem(
            HttpStatus status,
            PlatformErrorCode code,
            String detail,
            Map<String, Object> extensions,
            HttpHeaders headers,
            WebRequest request,
            Exception exception) {
        log.warn("Framework request error: code={}, status={}", code.value(), status.value());
        return response(status, code.value(), detail, extensions, headers, request);
    }

    private ResponseEntity<Object> response(
            HttpStatus status,
            String code,
            String detail,
            Map<String, Object> extensions,
            HttpHeaders headers,
            WebRequest request) {
        return response(status, code, detail, extensions, headers, servletRequest(request));
    }

    private ResponseEntity<Object> response(
            HttpStatus status,
            String code,
            String detail,
            Map<String, Object> extensions,
            HttpHeaders headers,
            HttpServletRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problemDetailFactory.create(status, code, detail, request, extensions), responseHeaders, status);
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        HttpServletRequest servletRequest = request instanceof NativeWebRequest nativeWebRequest
                ? nativeWebRequest.getNativeRequest(HttpServletRequest.class)
                : null;
        if (servletRequest == null) {
            throw new IllegalStateException("An HTTP servlet request is required to build a problem detail");
        }
        return servletRequest;
    }

    private static List<Map<String, String>> fieldErrors(List<FieldError> errors) {
        return errors.stream().map(error -> fieldError(error.getField(), "El valor no cumple las restricciones.")).toList();
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private static String parameterName(org.springframework.validation.method.ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "request" : name;
    }

    private static HttpStatus domainStatus(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
