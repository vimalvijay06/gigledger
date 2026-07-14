package com.gigledger.controller;

import com.gigledger.dto.DiscrepancyFlagResponse;
import com.gigledger.entity.User;
import com.gigledger.repository.UserRepository;
import com.gigledger.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnomalyDetectionService anomalyService;
    private final UserRepository          userRepository;

    // ── GET /analytics/flags ─────────────────────────────────────────────────
    // Returns the authenticated user's flagged discrepancy periods.
    // Same ownership rules as every other endpoint — JWT required.
    @GetMapping("/flags")
    public ResponseEntity<List<DiscrepancyFlagResponse>> getFlags(Authentication auth) {
        User user = getUser(auth.getName());
        return ResponseEntity.ok(anomalyService.getFlagsForUser(user));
    }

    // ── POST /analytics/run-detection ────────────────────────────────────────
    // Manually triggers the scheduled job for the authenticated user only.
    // Use this during testing instead of waiting 24 hours for the cron.
    //
    // Example (Postman / curl):
    //   POST http://localhost:8080/analytics/run-detection
    //   Authorization: Bearer <your-jwt>
    //
    // Returns: { "flagsCreated": <N> }
    @PostMapping("/run-detection")
    public ResponseEntity<Map<String, Integer>> runDetectionNow(Authentication auth) {
        User user = getUser(auth.getName());
        log.info("Manual anomaly detection triggered by user={}", user.getEmail());
        int created = anomalyService.detectAndSaveForUser(user);
        return ResponseEntity.ok(Map.of("flagsCreated", created));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found — please log in again."));
    }
}
