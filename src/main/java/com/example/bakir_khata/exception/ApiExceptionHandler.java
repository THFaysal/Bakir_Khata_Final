package com.example.bakir_khata.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations=RestController.class)
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler({BusinessRuleException.class,InvalidTransactionException.class,IllegalArgumentException.class})
    ResponseEntity<Map<String,String>> bad(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()==null?"Invalid request":ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error","Request failed validation."));
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String,String>> credentials(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Invalid credentials."));
    }
    @ExceptionHandler(UnauthorizedActionException.class)
    ResponseEntity<Map<String,String>> notAuthorized(UnauthorizedActionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Access denied."));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String,String>> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Access denied."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> internal(Exception ex) {
        log.error("API error",ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","Unexpected server error."));
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String,String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Not found."));
    }
}
