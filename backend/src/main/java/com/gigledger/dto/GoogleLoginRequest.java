package com.gigledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for Google Sign-In verification endpoint.
 */
@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
