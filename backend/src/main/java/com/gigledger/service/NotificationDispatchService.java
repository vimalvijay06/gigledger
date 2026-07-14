package com.gigledger.service;

import com.gigledger.entity.DiscrepancyFlag;
import com.gigledger.entity.DraftComplaint;
import com.gigledger.entity.NotificationLog;
import com.gigledger.entity.User;
import com.gigledger.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Dispatches notifications via Email.
 * Checks user preference settings and logs attempts to database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final EmailService emailService;
    private final NotificationLogRepository logRepository;

    /**
     * Sends notifications for a newly created discrepancy flag.
     */
    public void dispatchDiscrepancyFlag(User user, DiscrepancyFlag flag, java.math.BigDecimal shortfall) {
        log.info("Dispatching discrepancy flag notification for user={}", user.getEmail());

        if (user.isEmailNotificationsEnabled()) {
            String subject = "GigLedger: Pay discrepancy detected";
            String htmlBody = String.format(
                    "<h2>GigLedger Discrepancy Alert</h2>" +
                    "<p>Dear %s,</p>" +
                    "<p>A payment discrepancy has been detected in your orders for the period <strong>%s to %s</strong>.</p>" +
                    "<ul>" +
                    "  <li><strong>Platform:</strong> %s</li>" +
                    "  <li><strong>Baseline Rate:</strong> Rs. %.2f/km</li>" +
                    "  <li><strong>Observed Rate:</strong> Rs. %.2f/km</li>" +
                    "  <li><strong>Estimated Shortfall:</strong> Rs. %.2f</li>" +
                    "</ul>" +
                    "<p>Please review the details on the <a href=\"http://localhost:5173/analytics\">Analytics page</a>.</p>" +
                    "<br/><p>Best regards,<br/>The GigLedger Team</p>",
                    user.getName(), flag.getPeriodStart(), flag.getPeriodEnd(),
                    user.getPlatformPreference(), flag.getBaselineRate().doubleValue(),
                    flag.getObservedRate().doubleValue(), shortfall.doubleValue()
            );

            boolean success = emailService.sendEmail(user.getEmail(), subject, htmlBody);
            saveLog(user, "EMAIL", "DISCREPANCY_FLAG", success ? "SUCCESS" : "FAILED", 
                    success ? "Sent successfully via Resend" : "Resend dispatch failed", null);
        } else {
            log.info("Email notification disabled by user preference for user={}", user.getEmail());
            saveLog(user, "EMAIL", "DISCREPANCY_FLAG", "SKIPPED_USER_PREF", null, "Disabled in profile settings");
        }
    }

    /**
     * Sends notifications for a newly created complaint draft.
     */
    public void dispatchComplaintDraft(User user, DraftComplaint draft) {
        log.info("Dispatching complaint draft notification for user={}", user.getEmail());

        if (user.isEmailNotificationsEnabled()) {
            String subject = "GigLedger: Action Required - Complaint Draft Ready";
            String htmlBody = String.format(
                    "<h2>GigLedger Complaint Draft Ready</h2>" +
                    "<p>Dear %s,</p>" +
                    "<p>A legal grievance complaint draft has been automatically generated regarding underpayments on your %s orders.</p>" +
                    "<p>Please review and submit the draft on the <a href=\"http://localhost:5173/complaints\">Complaints page</a>.</p>" +
                    "<br/><p>Best regards,<br/>The GigLedger Team</p>",
                    user.getName(), draft.getPlatformName()
            );

            boolean success = emailService.sendEmail(user.getEmail(), subject, htmlBody);
            saveLog(user, "EMAIL", "COMPLAINT_DRAFT", success ? "SUCCESS" : "FAILED", 
                    success ? "Sent successfully via Resend" : "Resend dispatch failed", null);
        } else {
            log.info("Email notification disabled by user preference for user={}", user.getEmail());
            saveLog(user, "EMAIL", "COMPLAINT_DRAFT", "SKIPPED_USER_PREF", null, "Disabled in profile settings");
        }
    }

    private void saveLog(User user, String channel, String eventType, String status, String extMsgId, String failReason) {
        try {
            NotificationLog logEntry = NotificationLog.builder()
                    .user(user)
                    .channel(channel)
                    .eventType(eventType)
                    .sentAt(LocalDateTime.now())
                    .externalMessageId(extMsgId)
                    .status(status)
                    .failureReason(failReason)
                    .build();
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to write to notification_logs: {}", e.getMessage(), e);
        }
    }
}
