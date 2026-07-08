package com.gigledger.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

/**
 * Handles all JWT operations: generating tokens and validating/parsing them.
 *
 * We use HMAC-SHA256 (HS256) with a secret key injected from application.properties.
 * The subject of each token is the user's email — that's how we identify
 * the logged-in user on each request.
 */
@Service
public class JwtService {

    // Injected from application.properties: jwt.secret
    @Value("${jwt.secret}")
    private String secret;

    // Token validity in milliseconds (default 7 days); from jwt.expiration-ms
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Builds a signing Key from the configured secret string.
     * Keys.hmacShaKeyFor requires at least 256 bits (32 bytes) for HS256.
     */
    private Key signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Generate a signed JWT for the given email (subject).
     * The token contains: subject (email), issued-at, expiration.
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract the email (subject) from a valid token.
     * Throws JwtException if the token is expired, malformed, or the signature is invalid.
     */
    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Returns true if the token can be parsed and is not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
