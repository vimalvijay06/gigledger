package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response from POST /tasks/upload-screenshot.
 *
 * Returned BEFORE a Task is created — the frontend uses this to show
 * the OCR results in editable fields and let the user confirm or correct
 * the values before the Task is actually saved.
 *
 * tempFileRef: the stored filename on disk. This is sent back to the frontend
 *   and then included in the POST /tasks/confirm request so Spring Boot knows
 *   which file to associate with the final Task record.
 *
 * Confidence guide for the frontend:
 *   >= 0.75 → HIGH: pre-fill fields quietly
 *   0.45–0.74 → MEDIUM: pre-fill but show "please verify" note
 *   < 0.45 → LOW: show red warning on each affected field
 */
@Data
@Builder
public class UploadScreenshotResponse {

    /** Stored filename (UUID-based). Sent back in POST /tasks/confirm as tempFileRef. */
    private String tempFileRef;

    /** Fare extracted from the screenshot, in rupees. Null if OCR couldn't read it. */
    private BigDecimal promisedAmount;

    /** Distance extracted from the screenshot, in km. Null if OCR couldn't read it. */
    private BigDecimal distanceKm;

    /** 0.0–1.0 confidence score. Below 0.5 triggers a warning in the UI. */
    private double ocrConfidence;

    /** Full raw text that Tesseract extracted — returned so the frontend can show it for debugging. */
    private String rawText;
}
