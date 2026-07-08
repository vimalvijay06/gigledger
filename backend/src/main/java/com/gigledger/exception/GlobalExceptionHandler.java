package com.gigledger.exception;

import com.gigledger.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler — intercepts exceptions thrown by any @RestController
 * and converts them to clean JSON using our ErrorResponse DTO.
 *
 * WHY THIS MATTERS:
 * Without this, Spring Boot returns a white-label HTML error page or a JSON
 * blob that includes the full stack trace and class names — leaking internal
 * implementation details to any caller (including attackers).
 *
 * EXCEPTIONS HANDLED:
 * - MethodArgumentNotValidException → 400 (bean validation failure on @Valid DTOs)
 * - NoSuchElementException          → 404 (task/user not found)
 * - IllegalStateException           → 409 (e.g. payout already logged)
 * - IllegalArgumentException        → 400 (bad input caught in service)
 * - BadCredentialsException         → 401 (wrong password)
 * - AccessDeniedException           → 403 (ownership check failure)
 * - Exception (catch-all)           → 500 (unexpected errors; message is hidden)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 — Validation failure (@Valid) ───────────────────────────────────

    /**
     * Triggered when @Valid fails on a request body.
     * Collects ALL field-level errors into a single comma-separated message
     * so the caller sees every violation in one response, not just the first.
     *
     * Example output:
     *   "message": "promisedAmount: must be greater than zero; email: must be a valid email address"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, "Validation failed: " + message, request);
    }

    // ─── 400 — Bad argument ───────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ─── 401 — Wrong credentials ──────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {
        // Always return a generic message to prevent user enumeration
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    // ─── 403 — Ownership violation ────────────────────────────────────────────

    /**
     * Triggered by TaskService when a user tries to access another user's task.
     * Note: Spring Security's own filter-level 403 bypasses this handler;
     * this only catches service-layer AccessDeniedException.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request);
    }

    // ─── 404 — Not found ─────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoSuchElementException ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ─── 409 — Conflict ──────────────────────────────────────────────────────

    /**
     * Used for:
     * - "Email already registered" (signup)
     * - "Payout already logged for this task" (duplicate payout)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            IllegalStateException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ─── 500 — Catch-all ─────────────────────────────────────────────────────

    /**
     * Catches any unhandled exception.
     * The real cause is logged server-side but NOT exposed to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        // Log the real error server-side (visible in mvn spring-boot:run output)
        System.err.println("[GlobalExceptionHandler] Unhandled exception: " + ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // ─── Builder helper ───────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
