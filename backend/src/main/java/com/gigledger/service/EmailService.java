package com.gigledger.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Handles email notifications via Resend API and simulates official grievance submissions.
 */
@Slf4j
@Service
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    private final WebClient webClient;

    public EmailService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    /**
     * Sends an email via Resend's REST API.
     * Returns true on success, false on failure. Safe from throwing unhandled exceptions.
     */
    public boolean sendEmail(String toAddress, String subject, String htmlBody) {
        log.info("Dispatching email via Resend: to={}, subject='{}'", toAddress, subject);

        if (resendApiKey == null || resendApiKey.trim().isEmpty()) {
            log.warn("RESEND_API_KEY is not configured. Email dispatch skipped.");
            return false;
        }

        try {
            ResendEmailRequest request = new ResendEmailRequest(
                    "onboarding@resend.dev",
                    toAddress,
                    subject,
                    htmlBody
            );

            ResendEmailResponse response = webClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ResendEmailResponse.class)
                    .block();

            if (response != null && response.getId() != null) {
                log.info("Email sent successfully via Resend. Message ID: {}", response.getId());
                return true;
            } else {
                log.error("Failed to send email: Resend returned empty response.");
                return false;
            }
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Resend API returned error status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error occurred while calling Resend API: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Simulates sending a legal grievance email on behalf of a worker.
     * In a live deployment, this would use a SendGrid/Resend API or SMTP transport.
     * Enforces the 'Reply-To' header to ensure responses go directly to the worker.
     */
    public boolean sendGrievance(String toEmail, String workerEmail, String subject, String body) {
        log.info("=====================================================================");
        log.info("🚨 [EMAIL SERVICE] DISPATCHING OFFICIAL GRIEVANCE COMPLAINT");
        log.info("From: noreply@gigledger.com (GigLedger Redressal Agent)");
        log.info("To: {}", toEmail);
        log.info("Reply-To: {}", workerEmail);
        log.info("Subject: {}", subject);
        log.info("------------------------- EMAIL BODY START -------------------------");
        for (String line : body.split("\n")) {
            log.info("{}", line);
        }
        log.info("-------------------------- EMAIL BODY END --------------------------");
        log.info("=====================================================================");
        
        // Return true to indicate successful simulation/dispatch.
        return true;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ResendEmailRequest {
        private String from;
        private String to;
        private String subject;
        private String html;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ResendEmailResponse {
        private String id;
    }
}
