package com.wastecollect.auth;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "AUTHENTICATION_ERROR", "message", exception.getMessage()));
    }

    @ExceptionHandler({NoSuchElementException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> requestError(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "REQUEST_ERROR", "message", exception.getMessage()));
    }
}
