package com.gigledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response body for both /auth/signup and /auth/login.
 * The frontend stores the token in localStorage and sends it as
 * "Authorization: Bearer <token>" on every subsequent request.
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String name;
    private String email;
}
