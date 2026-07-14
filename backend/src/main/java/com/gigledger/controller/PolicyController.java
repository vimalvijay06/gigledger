package com.gigledger.controller;

import com.gigledger.dto.PolicyUpdateResponse;
import com.gigledger.service.PolicyPulseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/policy")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyPulseService policyService;

    // ── POST /policy/ingest ──────────────────────────────────────────────────
    @PostMapping("/ingest")
    public ResponseEntity<java.util.Map<String, String>> triggerIngest() {
        log.info("Manual policy ingestion triggered.");
        policyService.runIngestionScheduler();
        return ResponseEntity.ok(java.util.Map.of("message", "Policy ingestion run triggered successfully"));
    }

    // ── GET /policy/feed ─────────────────────────────────────────────────────
    @GetMapping("/feed")
    public ResponseEntity<List<PolicyUpdateResponse>> getPolicyFeed(
            @RequestParam(name = "lang", defaultValue = "en") String lang) {
        log.info("Policy news feed requested with lang={}.", lang);
        return ResponseEntity.ok(policyService.getFeed(lang));
    }

    // ── GET /policy/{update_id}/narrate ──────────────────────────────────────
    @GetMapping(value = "/{update_id}/narrate", produces = "audio/wav")
    public ResponseEntity<byte[]> getAudioNarration(
            @PathVariable("update_id") UUID updateId,
            @RequestParam(name = "lang", defaultValue = "en") String lang) {
        
        log.info("Audio narration stream requested for updateId={} lang={}", updateId, lang);
        byte[] audioBytes = policyService.getAudioNarration(updateId, lang);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"narration.wav\"")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .contentLength(audioBytes.length)
                .body(audioBytes);
    }
}
