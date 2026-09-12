package com.forgeshift.profile.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error is written as JSON, whatever the request asked for.
 *
 * <p>Without a preset content type, Spring negotiates the error body against the
 * request's Accept header. For a caller that accepts only something else -
 * {@code application/octet-stream}, say - it finds no converter that writes this map
 * as that type and abandons the handler
 * ({@code Failure in @ExceptionHandler ... HttpMediaTypeNotAcceptableException}). The
 * original exception then fell through to the container's {@code /error} page, which
 * the security chain answered 401 {@code invalid_jwt_token} - so a failure looked
 * exactly like being signed out. A preset content type is used as-is, so the real
 * status and message reach the caller.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> fe = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fe.put(f.getField(), f.getDefaultMessage()));
        body.put("fieldErrors", fe);
        return json(HttpStatus.BAD_REQUEST, body);
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ProfileNotFoundException e) {
        return json(HttpStatus.NOT_FOUND, base(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> route(NoResourceFoundException e) {
        return json(HttpStatus.NOT_FOUND, base(HttpStatus.NOT_FOUND,
                "No endpoint mapped for " + e.getHttpMethod() + " /" + e.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badArg(IllegalArgumentException e) {
        return json(HttpStatus.BAD_REQUEST, base(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return json(HttpStatus.CONFLICT, base(HttpStatus.CONFLICT, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unknown(Exception e) {
        log.error("Unhandled exception", e);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, base(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
    }

    private static ResponseEntity<Map<String, Object>> json(HttpStatus status, Map<String, Object> body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static Map<String, Object> base(HttpStatus status, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", msg);
        return body;
    }
}
