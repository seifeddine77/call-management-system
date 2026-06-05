package com.pfe.callmgmt.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR = "error";
    private static final java.time.ZoneId ZONE_PARIS = java.time.ZoneId.of("Europe/Paris");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 400);
        body.put(KEY_ERROR, "Erreur de validation");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 404);
        body.put(KEY_ERROR, ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 409);
        body.put(KEY_ERROR, ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 401);
        body.put(KEY_ERROR, ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 403);
        body.put(KEY_ERROR, "Accès refusé");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidTransitionException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 400);
        body.put(KEY_ERROR, ex.getMessage());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Erreur inattendue: ", ex);

        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(ZONE_PARIS).toString());
        body.put(KEY_STATUS, 500);
        body.put(KEY_ERROR, "Erreur interne du serveur");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
