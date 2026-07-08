package com.gigledger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /auth/signup.
 *
 * Validation rules (enforced by @Valid before reaching the service layer):
 * - name:     must not be blank
 * - email:    must not be blank AND must be a well-formed email address
 * - password: must not be blank AND at least 6 characters long
 *
 * @NotBlank already implies @NotNull, so no separate @NotNull is needed
 * for String fields. @NotNull is only required on non-String types (e.g. BigDecimal).
 */
@Data
public class SignupRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address (e.g. user@example.com)")
    @Size(max = 255, message = "Email must be 255 characters or fewer")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must be between 6 and 72 characters")
    // max 72: BCrypt silently truncates at 72 bytes — surfacing this as a validation error
    // is better than silently accepting a longer password and truncating it.
    private String password;
}
