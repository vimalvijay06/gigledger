package com.gigledger.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /auth/signup.
 *
 * @NotBlank and @Email are validated by Spring's @Valid before the
 * request reaches the service layer — no manual null checks needed.
 */
@Data  // Lombok: generates getters, setters, equals, hashCode, toString
public class SignupRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}
