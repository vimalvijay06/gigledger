package com.gigledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data representation of Google Tokeninfo API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenInfo(
    String iss,
    String sub,
    String aud,
    String email,
    String name,
    String email_verified
) {}
