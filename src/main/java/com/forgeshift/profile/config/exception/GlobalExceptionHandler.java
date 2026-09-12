package com.forgeshift.profile.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * A request whose Accept header rules out everything the endpoint sends - a JSON
     * answer asked for as {@code application/octet-stream}, say - is a 406. The catch-all
     * below reported it as a 500, as though the service had broken, when often the
     * endpoint had done its work and only its answer could not be sent. The body names
     * what the endpoint does send.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> notAcceptable(HttpMediaTypeNotAcceptableException e) {
        List<MediaType> sendable = e.getSupportedMediaTypes();
        String message = sendable.isEmpty() ? e.getMessage()
                : e.getMessage() + "; this endpoint answers with " + MediaType.toString(sendable);
        return json(HttpStatus.NOT_ACCEPTABLE, base(HttpStatus.NOT_ACCEPTABLE, message));
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
