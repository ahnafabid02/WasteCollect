package com.wastecollect.auth;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.List;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "AUTHENTICATION_ERROR", "message", exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "REQUEST_ERROR", "message", exception.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "CONFLICT", "message", exception instanceof DataIntegrityViolationException ? "The operation conflicts with current data" : exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of("field", error.getField(), "reason", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_ERROR", "message", "Request validation failed", "details", details));
    }
}
