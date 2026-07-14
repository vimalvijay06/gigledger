package com.gigledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request body for POST /tasks/confirm.
 *
 * The user has reviewed the OCR results (possibly corrected them) and
 * now wants to create the actual Task record.
 *
 * tempFileRef: the filename we returned in UploadScreenshotResponse.
 *   We use this to set screenshotUrl on the Task entity.
 *
 * ocrConfidence: echoed back from the upload response so we can store it
 *   on the Task without re-running OCR.
 *
 * promisedAmount / distanceKm / acceptedAt: may have been manually corrected
 *   by the user — use these values, not the raw OCR output.
 */
@Data
public class ConfirmTaskRequest {

    /**
     * The filename returned by POST /tasks/upload-screenshot.
     * Must not be blank — if there's no screenshot we should use POST /tasks instead.
     */
    @NotBlank(message = "tempFileRef is required")
    private String tempFileRef;

    /**
     * The OCR confidence score from the upload step.
     * Stored on the Task for analytics (Phase 3 scope).
     */
    @NotNull(message = "ocrConfidence is required")
    private Double ocrConfidence;

    /**
     * Final promised fare — may have been corrected by the user.
     * Must be a positive value.
     */
    @NotNull(message = "promisedAmount is required")
    @Positive(message = "promisedAmount must be greater than 0")
    private BigDecimal promisedAmount;

    /**
     * Final delivery distance in km — optional (some screenshots don't show distance).
     */
    @Positive(message = "distanceKm must be greater than 0 if provided")
    private BigDecimal distanceKm;

    /**
     * When the worker accepted the task (user-supplied, matches Phase 1 TaskRequest).
     */
    @NotNull(message = "acceptedAt is required")
    private LocalDateTime acceptedAt;
}
