package com.gigledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Uniform JSON error response shape returned by GlobalExceptionHandler.
 *
 * Every error, regardless of cause, will look like:
 * {
 *   "timestamp": "2024-07-08T09:30:00",
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "promisedAmount: must be greater than zero",
 *   "path":      "/tasks"
 * }
 *
 * This replaces the Spring Boot default white-label error page / stack trace
 * that would otherwise leak internal implementation details.
 */
@Getter
public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp = LocalDateTime.now();

    private final int status;
    private final String error;
    private final String message;
    private final String path;

    public ErrorResponse(int status, String error, String message, String path) {
        this.status  = status;
        this.error   = error;
        this.message = message;
        this.path    = path;
    }
}
