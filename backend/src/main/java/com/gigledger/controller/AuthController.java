package com.gigledger.controller;

import com.gigledger.dto.AuthResponse;
import com.gigledger.dto.LoginRequest;
import com.gigledger.dto.SignupRequest;
import com.gigledger.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * Exception handling is delegated entirely to GlobalExceptionHandler:
 * - IllegalStateException  → 409 (email already registered)
 * - BadCredentialsException → 401 (wrong password)
 * - MethodArgumentNotValidException → 400 (bean validation on @Valid)
 *
 * This keeps the controller thin — it only handles routing and HTTP status codes
 * for the happy path. All error paths go through the global handler.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
