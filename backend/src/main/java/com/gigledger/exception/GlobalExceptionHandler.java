package com.gigledger.exception;

import com.gigledger.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler — converts exceptions thrown by @RestControllers
 * into clean JSON responses using ErrorResponse.
 *
 * ── CRITICAL: Do NOT add @ExceptionHandler(AccessDeniedException.class) here ──
 * Spring Security 6 handles AccessDeniedException in its Servlet Filter chain,
 * which runs BEFORE Spring MVC. Registering an @ExceptionHandler for it in
 * @ControllerAdvice causes Spring Security 6 to incorrectly block even
 * permitAll() endpoints with an empty 403 body.
 *
 * Filter-level 401 and 403 are handled in SecurityConfig via:
 *   - AuthenticationEntryPoint  → 401 (no/invalid JWT)
 *   - AccessDeniedHandler       → 403 (Spring Security layer)
 *
 * Service-layer ownership violations (e.g. User B touches User A's task)
 * throw ResponseStatusException(FORBIDDEN) instead of AccessDeniedException,
 * which IS caught by @ExceptionHandler below.
 *
 * EXCEPTIONS HANDLED HERE:
 * - MethodArgumentNotValidException → 400 (bean validation on @Valid DTOs)
 * - IllegalArgumentException        → 400 (bad input in service)
 * - BadCredentialsException         → 401 (wrong password)
 * - ResponseStatusException         → uses embedded status (403, 404, 409 etc.)
 * - NoSuchElementException          → 404 (task/user not found)
 * - IllegalStateException           → 409 (e.g. payout already logged)
 * - Exception (catch-all)           → 500 (message hidden, logged server-side)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 — Validation failure (@Valid) ───────────────────────────────────

    /**
     * Triggered when @Valid fails on a request body.
     * Collects ALL field errors into one response so the caller sees every
     * violation at once, not just the first one.
     *
     * Example: "Validation failed: promisedAmount: must be greater than zero;
     *           email: must be a valid email address"
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

    // ─── 401 — Wrong credentials (service layer) ─────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {
        // Generic message prevents user enumeration (don't reveal if email exists)
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    // ─── Dynamic status (403 ownership, 404 not-found, 409 conflict) ─────────

    /**
     * ResponseStatusException carries its own HTTP status, so one handler
     * covers all service-layer cases where we need a specific HTTP code:
     *
     * - FORBIDDEN  (403): thrown by TaskService when User B tries to access User A's task
     * - NOT_FOUND  (404): alternative to NoSuchElementException
     * - CONFLICT   (409): alternative to IllegalStateException
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return build(status, message, request);
    }

    // ─── 404 — Not found ─────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoSuchElementException ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ─── 409 — Conflict ──────────────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            IllegalStateException ex,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ─── 500 — Catch-all ─────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        System.err.println("[GlobalExceptionHandler] Unhandled exception at "
            + request.getRequestURI() + ": " + ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            new ErrorResponse(status.value(), status.getReasonPhrase(), message, request.getRequestURI())
        );
    }
}
