package com.gigledger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /auth/login.
 *
 * Kept intentionally minimal — login error messages must NOT reveal
 * whether the email exists or the password is wrong (prevents user enumeration).
 * The service always returns the same "Invalid email or password" message.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
