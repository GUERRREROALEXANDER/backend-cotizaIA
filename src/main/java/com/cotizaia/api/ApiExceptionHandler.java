package com.cotizaia.api;

import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.ingest.UnsupportedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps ingest domain errors to HTTP statuses: bad payload/channel to 400 and a
 * missing client/brief to 404, so callers get a signal instead of a 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> badCredentials(BadCredentialsException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(IllegalStateException exception) {
        return Map.of("error", String.valueOf(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return Map.of("error", "Validation failed", "fields", fields);
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
}
