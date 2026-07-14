package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PolicyUpdateResponse {
    private UUID id;
    private String title;
    private String sourceUrl;
    private String summary;
    private String category;
    private String urgency;
    private String reasoning;
    private String categoryHint;
    private LocalDateTime publishedAt;
}
