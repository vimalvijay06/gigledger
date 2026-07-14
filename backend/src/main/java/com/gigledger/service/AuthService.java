package com.gigledger.service;

import com.gigledger.dto.AuthResponse;
import com.gigledger.dto.GoogleLoginRequest;
import com.gigledger.dto.GoogleTokenInfo;
import com.gigledger.dto.LoginRequest;
import com.gigledger.dto.SignupRequest;
import com.gigledger.entity.User;
import com.gigledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Handles user registration and login.
 *
 * Spring Security's PasswordEncoder (BCrypt) is injected from SecurityConfig.
 * We never store or return the raw password — only the BCrypt hash.
 */
@Slf4j
@Service
@RequiredArgsConstructor  // Lombok: generates constructor for all final fields (constructor injection)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${google.client.id}")
    private String googleClientId;

    /**
     * Register a new user.
     * Throws IllegalStateException if the email is already registered
     * (controller converts this to 409 Conflict).
     */
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    /**
     * Authenticate an existing user.
     * BadCredentialsException → Spring Security maps this to 401 Unauthorized.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    /**
     * Verifies Google ID token and performs login or automatic signup.
     */
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenInfo tokenInfo;
        try {
            WebClient webClient = WebClient.builder().baseUrl("https://oauth2.googleapis.com").build();
            tokenInfo = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tokeninfo")
                            .queryParam("id_token", request.getIdToken())
                            .build())
                    .retrieve()
                    .bodyToMono(GoogleTokenInfo.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to call Google tokeninfo API: {}", e.getMessage());
            throw new BadCredentialsException("Google token verification failed: " + e.getMessage());
        }

        if (tokenInfo == null) {
            throw new BadCredentialsException("Google token verification returned empty response");
        }

        // Validate Issuer
        boolean validIssuer = "accounts.google.com".equals(tokenInfo.iss()) || 
                             "https://accounts.google.com".equals(tokenInfo.iss());
        if (!validIssuer) {
            log.warn("Google login rejected: Invalid issuer: {}", tokenInfo.iss());
            throw new BadCredentialsException("Invalid token issuer");
        }

        // Validate Email Verification
        boolean emailVerified = "true".equalsIgnoreCase(tokenInfo.email_verified()) || 
                                Boolean.parseBoolean(tokenInfo.email_verified());
        if (!emailVerified) {
            log.warn("Google login rejected: Email not verified: {}", tokenInfo.email());
            throw new BadCredentialsException("Google email is not verified");
        }

        // Validate Audience (Client ID)
        if (googleClientId != null && !googleClientId.trim().isEmpty()) {
            if (!googleClientId.equals(tokenInfo.aud())) {
                log.warn("Google login rejected: Audience mismatch. Configured: {}, Token: {}", googleClientId, tokenInfo.aud());
                throw new BadCredentialsException("Invalid token audience");
            }
        } else {
            log.warn("Google Client ID is not configured in backend properties. Skipping audience validation check.");
        }

        // Register or load the user
        User user = userRepository.findByEmail(tokenInfo.email())
                .orElseGet(() -> {
                    log.info("Creating new account for Google user: {}", tokenInfo.email());
                    User newUser = User.builder()
                            .email(tokenInfo.email())
                            .name(tokenInfo.name() != null ? tokenInfo.name() : "Google User")
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .build();
                    return userRepository.save(newUser);
                });

        log.info("Successful Google login for user: {}", user.getEmail());
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail());
    }
}

