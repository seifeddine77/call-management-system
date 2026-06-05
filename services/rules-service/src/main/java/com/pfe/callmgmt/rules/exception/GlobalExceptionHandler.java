package com.pfe.callmgmt.rules.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestion globale des erreurs pour le rules-service
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Erreur: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(java.time.ZoneId.of("Europe/Paris")));
        body.put(KEY_STATUS, 404);
        body.put(KEY_ERROR, "Not Found");
        body.put(KEY_MESSAGE, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(java.time.ZoneId.of("Europe/Paris")));
        body.put(KEY_STATUS, 400);
        body.put(KEY_ERROR, "Validation Error");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        body.put("errors", errors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(java.time.ZoneId.of("Europe/Paris")));
        body.put(KEY_STATUS, 400);
        body.put(KEY_ERROR, "Bad Request");
        body.put(KEY_MESSAGE, ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.error("Ressource non trouvée: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, LocalDateTime.now(java.time.ZoneId.of("Europe/Paris")));
        body.put(KEY_STATUS, 404);
        body.put(KEY_ERROR, "Not Found");
        body.put(KEY_MESSAGE, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
