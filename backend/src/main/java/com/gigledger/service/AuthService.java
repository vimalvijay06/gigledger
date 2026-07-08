package com.gigledger.service;

import com.gigledger.dto.AuthResponse;
import com.gigledger.dto.LoginRequest;
import com.gigledger.dto.SignupRequest;
import com.gigledger.entity.User;
import com.gigledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration and login.
 *
 * Spring Security's PasswordEncoder (BCrypt) is injected from SecurityConfig.
 * We never store or return the raw password — only the BCrypt hash.
 */
@Service
@RequiredArgsConstructor  // Lombok: generates constructor for all final fields (constructor injection)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
}
