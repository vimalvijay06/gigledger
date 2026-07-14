package com.gigledger.config;

import com.gigledger.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * Key decisions:
 * - CSRF disabled: we use stateless JWT, not session cookies, so CSRF doesn't apply
 * - Session policy STATELESS: no HttpSession is created or used
 * - CORS configured to allow the React dev server (localhost:5173)
 * - /auth/** is public; everything else requires a valid JWT
 * - JwtAuthFilter runs before Spring's own UsernamePasswordAuthenticationFilter
 *
 * IMPORTANT — Why 401/403 are handled here, not in GlobalExceptionHandler:
 * Spring Security 6 runs its authorization checks in a Servlet Filter, which
 * executes BEFORE Spring MVC (and therefore before @ControllerAdvice/@ExceptionHandler).
 * If @ExceptionHandler(AccessDeniedException.class) is registered in @ControllerAdvice,
 * Spring Security 6's internal authorization model gets confused and can block
 * even permitAll() routes with a 403 (empty body).
 *
 * The correct pattern: configure AccessDeniedHandler and AuthenticationEntryPoint
 * directly on the filter chain — they run at the right layer and can write
 * JSON responses without interfering with Spring MVC.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless REST APIs
            .csrf(csrf -> csrf.disable())

            // Allow the React frontend to call the API
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // No sessions — every request must carry its own JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route-level authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()   // signup and login are public
                .requestMatchers("/user/fuel-price").permitAll() // permit public fuel testing
                .anyRequest().authenticated()              // everything else needs a JWT
            )

            // ── Filter-level error handlers (run BEFORE Spring MVC) ──────────
            // These MUST be configured here, not in @ControllerAdvice, because
            // Spring Security's filter chain runs before @ExceptionHandler.
            .exceptionHandling(ex -> ex

                // 401 — No JWT at all, or JWT is invalid/expired
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(buildErrorJson(
                        401, "Unauthorized",
                        "Authentication required — include a valid Bearer token",
                        request.getRequestURI()
                    ));
                })

                // 403 — Authenticated but not allowed (e.g. hitting a secured endpoint
                // with a valid token but insufficient authority)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(buildErrorJson(
                        403, "Forbidden",
                        "You do not have permission to access this resource",
                        request.getRequestURI()
                    ));
                })
            )

            // Register our JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Builds a minimal JSON error body at the filter level.
     * We can't use Jackson's ObjectMapper here via Spring (it's not wired in filters),
     * so we build the JSON string directly to keep this dependency-free.
     */
    private String buildErrorJson(int status, String error, String message, String path) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        return String.format(
            "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
            timestamp, status, error, message, path
        );
    }

    /**
     * BCrypt is the industry-standard for password hashing.
     * The default work factor (10) is strong enough for Phase 1.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS policy: allow the React dev server to call this API.
     * In production, replace localhost:5173 with the actual frontend domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
