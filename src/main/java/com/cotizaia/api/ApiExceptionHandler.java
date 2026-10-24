package com.cotizaia.api;

import com.cotizaia.agent.PipelineRunRecorder.PipelineFailedException;
import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.ingest.UnsupportedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to a JSON {@code {"error": ...}} body with a meaningful status:
 * 400 invalid input (body validation, malformed JSON, bad parameter type, missing or invalid parameter,
 * unsupported channel), 401 bad credentials, 404 missing or foreign-agency resource and unknown route,
 * 405 wrong method, 409 conflicting data, 415 wrong content type, 422 illegal lifecycle move,
 * 503 failed agent run (with {@code executionId}) and 500 for anything unexpected, whose details are
 * logged but never returned. Missing or invalid tokens are rejected earlier by the security filters.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> badCredentials(BadCredentialsException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(RuntimeException exception) {
        String message = exception instanceof DataIntegrityViolationException
                ? "Operation conflicts with existing data" : String.valueOf(exception.getMessage());
        return Map.of("error", message);
    }

    @ExceptionHandler(PipelineFailedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> pipelineFailed(PipelineFailedException exception) {
        return Map.of("error", exception.getMessage(), "executionId", exception.getExecutionId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return Map.of("error", "Validation failed", "fields", fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> parameterValidationFailed(HandlerMethodValidationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                fields.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
        return Map.of("error", "Validation failed", "fields", fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return Map.of("error", "Invalid value for parameter '" + exception.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> missingParameter(MissingServletRequestParameterException exception) {
        return Map.of("error", "Missing required parameter '" + exception.getParameterName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> malformedJson(HttpMessageNotReadableException exception) {
        return Map.of("error", "Malformed JSON request");
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> invalidStateTransition(InvalidStateTransitionException exception) {
        return Map.of("error", exception.getMessage(), "from", exception.getFrom().name(),
                "to", exception.getTo().name());
    }

    @ExceptionHandler({IllegalArgumentException.class, UnsupportedChannelException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(RuntimeException exception) {
        return Map.of("error", String.valueOf(exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException exception) {
        return Map.of("error", String.valueOf(exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> unknownRoute(NoResourceFoundException exception) {
        return Map.of("error", "Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Map<String, String> methodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return Map.of("error", "Method not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Map<String, String> mediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return Map.of("error", "Unsupported media type");
    }

    /** Keeps the status of any other Spring MVC error (e.g. 406) and turns everything else into a 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            String reason = status instanceof HttpStatus known ? known.getReasonPhrase() : "Request failed";
            return ResponseEntity.status(status).body(Map.of("error", reason));
        }
        LOGGER.error("Unhandled request failure", exception);
        return ResponseEntity.internalServerError().body(Map.of("error", "Unexpected server error"));
    }
}
