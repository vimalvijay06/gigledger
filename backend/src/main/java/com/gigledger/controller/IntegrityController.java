package com.gigledger.controller;

import com.gigledger.entity.User;
import com.gigledger.repository.UserRepository;
import com.gigledger.service.IntegrityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/integrity")
@RequiredArgsConstructor
public class IntegrityController {

    private final IntegrityService integrityService;
    private final UserRepository   userRepository;

    // ── GET /integrity/verify ────────────────────────────────────────────────
    // Cryptographically checks the entire database hash chain for this user.
    // Returns verification success status and the ID of the first modified record.
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyIntegrity(Authentication auth) {
        User user = getUser(auth.getName());
        log.info("Integrity check requested for user={}", user.getEmail());

        IntegrityService.VerificationResult result = integrityService.verifyChain(user);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("valid", result.valid());
        response.put("total_records", result.totalRecords());
        response.put("broken_at", result.brokenAt() != null ? result.brokenAt().toString() : null);

        return ResponseEntity.ok(response);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found — please log in again."));
    }
}
