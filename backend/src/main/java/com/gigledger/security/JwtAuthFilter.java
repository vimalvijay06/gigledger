package com.gigledger.security;

import com.gigledger.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Intercepts every HTTP request and validates the Bearer JWT.
 *
 * Flow:
 * 1. Read the "Authorization" header
 * 2. If it starts with "Bearer ", extract the token
 * 3. Validate the token; extract the email
 * 4. Set the authentication in the SecurityContext so Spring Security
 *    knows who is making the request
 *
 * OncePerRequestFilter guarantees this runs exactly once per request,
 * even in cases where the request is forwarded internally.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No token → continue the filter chain (Spring Security will reject
        // the request if the endpoint requires authentication)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7); // strip "Bearer "

        // Don't process if token is invalid (expired, wrong signature, etc.)
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Don't overwrite an existing authentication (e.g., from a previous filter)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractEmail(token);

        // Create a Spring Security Authentication object.
        // We use the email as the principal (no need to load the full UserDetails here
        // since the JWT itself is the proof of identity).
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,              // credentials — not needed after token validation
                Collections.emptyList()  // authorities — roles (none in Phase 1)
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}
