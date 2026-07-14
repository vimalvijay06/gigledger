package com.gigledger.controller;

import com.gigledger.entity.DraftComplaint;
import com.gigledger.entity.User;
import com.gigledger.repository.DraftComplaintRepository;
import com.gigledger.repository.UserRepository;
import com.gigledger.service.EmailService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/complaints")
@RequiredArgsConstructor
@Transactional
public class ComplaintController {

    private final UserRepository userRepository;
    private final DraftComplaintRepository draftComplaintRepository;
    private final EmailService emailService;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    @GetMapping
    public ResponseEntity<List<ComplaintResponse>> getPendingComplaints(Authentication auth) {
        User user = getUser(auth.getName());
        List<DraftComplaint> drafts = draftComplaintRepository.findByUserAndStatusOrderByCreatedAtDesc(user, "PENDING_REVIEW");
        
        List<ComplaintResponse> response = drafts.stream().map(d -> ComplaintResponse.builder()
                .id(d.getId())
                .platformName(d.getPlatformName())
                .grievanceEmail(d.getGrievanceEmail())
                .subject(d.getSubject())
                .draftText(d.getDraftText())
                .status(d.getStatus())
                .createdAt(d.getCreatedAt())
                .build()).collect(Collectors.toList());
                
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, String>> sendComplaint(
            @PathVariable UUID id,
            @RequestBody(required = false) SendComplaintRequest request,
            Authentication auth) {
        User user = getUser(auth.getName());
        DraftComplaint draft = draftComplaintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint draft not found."));

        if (!draft.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        if (!"PENDING_REVIEW".equalsIgnoreCase(draft.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint is not pending review.");
        }

        // Apply edits if any
        if (request != null && request.getDraftText() != null) {
            draft.setDraftText(request.getDraftText());
        }

        // Send Email
        boolean sent = emailService.sendGrievance(draft.getGrievanceEmail(), user.getEmail(), draft.getSubject(), draft.getDraftText());
        
        if (sent) {
            draft.setStatus("SENT");
            draftComplaintRepository.save(draft);
            log.info("Grievance email marked as SENT. user={}, complaintId={}", user.getEmail(), id);
            return ResponseEntity.ok(Map.of("message", "Grievance complaint sent successfully."));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to dispatch grievance email."));
        }
    }

    @Data
    public static class SendComplaintRequest {
        private String draftText;
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Map<String, String>> dismissComplaint(
            @PathVariable UUID id,
            @RequestBody DismissRequest request,
            Authentication auth) {
        User user = getUser(auth.getName());
        DraftComplaint draft = draftComplaintRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint draft not found."));

        if (!draft.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }

        if (!"PENDING_REVIEW".equalsIgnoreCase(draft.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complaint is not pending review.");
        }

        draft.setStatus("DISMISSED");
        draft.setDismissReason(request.getReason());
        draftComplaintRepository.save(draft);
        log.info("Complaint draft DISMISSED by user={}. Reason: '{}'", user.getEmail(), request.getReason());

        return ResponseEntity.ok(Map.of("message", "Complaint draft dismissed successfully."));
    }

    @Data
    @Builder
    public static class ComplaintResponse {
        private UUID id;
        private String platformName;
        private String grievanceEmail;
        private String subject;
        private String draftText;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class DismissRequest {
        private String reason;
    }
}
