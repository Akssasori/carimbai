package com.app.carimbai.handler;

import com.app.carimbai.execption.DuplicateIdempotencyKeyException;
import com.app.carimbai.execption.TooManyStampsException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> conflict(IllegalStateException ex) {
        // Use para replay token, etc.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> accessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION", "message", ex.getBindingResult().toString()));
    }

    @ExceptionHandler(TooManyStampsException.class)
    public ResponseEntity<?> tooMany(TooManyStampsException ex) {
        return ResponseEntity.status(429).body(Map.of("error","TOO_MANY_REQUESTS","message",ex.getMessage()));
    }

//    @ExceptionHandler(OptimisticLockingFailureException.class)
//    public ResponseEntity<?> optimisticLockingFailure(OptimisticLockingFailureException ex) {
//        return ResponseEntity.status(409).body(Map.of("error","OPTIMISTIC_LOCKING_FAILURE","message",ex.getMessage()));
//    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> optimistic(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(409).body(Map.of("error","CONFLICT","message","Concurrent update on Card"));
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<?> duplicateIdem(DuplicateIdempotencyKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
    }
}
