package com.gigledger.service;

import com.gigledger.dto.DiscrepancyFlagResponse;
import com.gigledger.entity.DiscrepancyFlag;
import com.gigledger.entity.PlatformGrievanceContact;
import com.gigledger.entity.DraftComplaint;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.DiscrepancyFlagRepository;
import com.gigledger.repository.TaskRepository;
import com.gigledger.repository.UserRepository;
import com.gigledger.repository.PlatformGrievanceContactRepository;
import com.gigledger.repository.DraftComplaintRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Orchestrates weekly pay discrepancy analysis by coordinating with Python ML microservice
 * and dispatching notifications/drafting complaints.
 */
@Slf4j
@Service
public class AnomalyDetectionService {

    private final UserRepository                       userRepository;
    private final TaskRepository                       taskRepository;
    private final DiscrepancyFlagRepository            flagRepository;
    private final PlatformGrievanceContactRepository   contactRepository;
    private final DraftComplaintRepository             draftComplaintRepository;
    private final WebClient                            webClient;
    private final IntegrityService                     integrityService;
    private final NotificationDispatchService          notificationDispatchService;

    @Autowired
    @Lazy
    private AnomalyDetectionService self;

    @Value("${ocr.service.url:http://localhost:8001}")
    private String mlServiceUrl;

    public AnomalyDetectionService(
            UserRepository userRepository,
            TaskRepository taskRepository,
            DiscrepancyFlagRepository flagRepository,
            PlatformGrievanceContactRepository contactRepository,
            DraftComplaintRepository draftComplaintRepository,
            IntegrityService integrityService,
            NotificationDispatchService notificationDispatchService,
            @Value("${ocr.service.url:http://localhost:8001}") String mlServiceUrl) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.flagRepository = flagRepository;
        this.contactRepository = contactRepository;
        this.draftComplaintRepository = draftComplaintRepository;
        this.integrityService = integrityService;
        this.notificationDispatchService = notificationDispatchService;
        this.mlServiceUrl   = mlServiceUrl;
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
        log.info("AnomalyDetectionService configured — ML service URL: {}", mlServiceUrl);
    }

    // Minimum tasks a user must have before we attempt anomaly detection.
    private static final int MIN_TASKS_FOR_ANALYSIS = 30;

    // ── Internal DTOs for talking to the Python ML service ───────────────────

    record TaskPayload(
        String task_id,
        double promised_amount,
        double actual_amount,
        double distance_km,
        String accepted_at   // ISO-8601
    ) {}

    record FlagPayload(
        String     period_start,
        String     period_end,
        String     bucket,
        double     baseline_rate,
        double     observed_rate,
        String     severity,
        double     sd_below
    ) {}

    // ── Scheduled job ─────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 2 * * *")
    public void runDailyDetection() {
        int page = 0;
        int size = 100;
        int flagsCreated = 0;
        Page<User> userPage;
        
        log.info("Anomaly detection scheduled job starting");
        
        do {
            userPage = userRepository.findAll(PageRequest.of(page, size));
            log.info("Processing page {}/{} ({} users)", page, userPage.getTotalPages(), userPage.getNumberOfElements());
            
            for (User user : userPage) {
                try {
                    flagsCreated += self.detectAndSaveForUser(user);
                } catch (Exception e) {
                    log.error("Anomaly detection failed for user={}: {}", user.getEmail(), e.getMessage());
                }
            }
            page++;
        } while (userPage.hasNext());

        log.info("Anomaly detection scheduled job complete — {} new flag(s) created", flagsCreated);
    }

    public int detectAndSaveForUser(User user) {
        List<Task> tasks = taskRepository.findByUserOrderByAcceptedAtDesc(user);

        List<Task> tasksWithPayout = tasks.stream()
                .filter(t -> t.getPayout() != null && t.getDistanceKm() != null)
                .toList();

        if (tasksWithPayout.size() < MIN_TASKS_FOR_ANALYSIS) {
            log.debug("user={} has only {} eligible tasks — skipping (minimum {})",
                    user.getEmail(), tasksWithPayout.size(), MIN_TASKS_FOR_ANALYSIS);
            return 0;
        }

        List<TaskPayload> payload = tasksWithPayout.stream()
                .map(t -> new TaskPayload(
                        t.getId().toString(),
                        t.getPromisedAmount().doubleValue(),
                        t.getPayout().getActualAmount().doubleValue(),
                        t.getDistanceKm().doubleValue(),
                        t.getAcceptedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                ))
                .collect(Collectors.toList());

        List<FlagPayload> flags;
        try {
            flags = webClient.post()
                    .uri(mlServiceUrl + "/analytics/detect-anomaly")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToFlux(FlagPayload.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ML service returned {} for user={}: {}", e.getStatusCode(), user.getEmail(), e.getResponseBodyAsString());
            return 0;
        } catch (Exception e) {
            log.error("Failed to call ML service for user={}: {}", user.getEmail(), e.getMessage());
            return 0;
        }

        if (flags == null || flags.isEmpty()) {
            log.debug("No anomalies detected for user={}", user.getEmail());
            return 0;
        }

        return self.saveFlagsAndDrafts(user, flags);
    }

    @Transactional
    public int saveFlagsAndDrafts(User user, List<FlagPayload> flags) {
        int saved = 0;
        for (FlagPayload flag : flags) {
            LocalDate start = LocalDate.parse(flag.period_start(), DateTimeFormatter.ISO_DATE);
            LocalDate end   = LocalDate.parse(flag.period_end(),   DateTimeFormatter.ISO_DATE);

            boolean exists = flagRepository.existsByUserAndPeriodStartAndPeriodEndAndBucket(
                    user, start, end, flag.bucket());
            if (exists) {
                log.debug("Flag already exists for user={} period={}/{} bucket={} — skipping",
                        user.getEmail(), start, end, flag.bucket());
                continue;
            }

            DiscrepancyFlag entity = DiscrepancyFlag.builder()
                    .user(user)
                    .periodStart(start)
                    .periodEnd(end)
                    .bucket(flag.bucket())
                    .baselineRate(BigDecimal.valueOf(flag.baseline_rate()))
                    .observedRate(BigDecimal.valueOf(flag.observed_rate()))
                    .severity(flag.severity())
                    .sdBelow(BigDecimal.valueOf(flag.sd_below()))
                    .build();

            flagRepository.save(entity);
            saved++;
            log.info("New flag saved: user={} period={}/{} bucket={} severity={}",
                     user.getEmail(), start, end, flag.bucket(), flag.severity());

            integrityService.appendRecord(user, "flag", entity.getId(), entity);

            try {
                checkAndCreateDraftComplaint(user, entity);
            } catch (Exception e) {
                log.error("Failed to check or create draft complaint for flag {}: {}", entity.getId(), e.getMessage());
            }
        }

        return saved;
    }

    private void checkAndCreateDraftComplaint(User user, DiscrepancyFlag flag) {
        // 1. Fetch matched tasks in flagged week and bucket
        List<Task> matchedTasks = taskRepository.findByUserOrderByAcceptedAtDesc(user).stream()
                .filter(t -> {
                    LocalDate taskDate = t.getAcceptedAt().toLocalDate();
                    return (taskDate.isEqual(flag.getPeriodStart()) || taskDate.isAfter(flag.getPeriodStart())) &&
                           (taskDate.isEqual(flag.getPeriodEnd()) || taskDate.isBefore(flag.getPeriodEnd())) &&
                           flag.getBucket().equals(classifyBucket(t.getAcceptedAt()));
                })
                .collect(Collectors.toList());

        // 2. Compute shortfall
        BigDecimal shortfall = matchedTasks.stream()
                .filter(t -> t.getPayout() != null)
                .map(t -> t.getPromisedAmount().subtract(t.getPayout().getActualAmount()))
                .filter(diff -> diff.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        // Dispatch Discrepancy Flag Notification (Email dispatching)
        try {
            notificationDispatchService.dispatchDiscrepancyFlag(user, flag, shortfall);
        } catch (Exception e) {
            log.error("Failed to dispatch discrepancy flag notification for flag {}: {}", flag.getId(), e.getMessage(), e);
        }

        // 3. Evaluate threshold: shortfall >= user shortfallThreshold OR flag severity == "high"
        BigDecimal threshold = user.getShortfallThreshold() != null ? user.getShortfallThreshold() : new BigDecimal("500.00");
        boolean meetsThreshold = shortfall.compareTo(threshold) >= 0 || "high".equalsIgnoreCase(flag.getSeverity());

        if (!meetsThreshold) {
            log.info("Flag {} shortfall={} (threshold={}) and severity={} does not meet complaint draft trigger.", 
                     flag.getId(), shortfall, threshold, flag.getSeverity());
            return;
        }

        // Prevent duplicate drafts
        if (draftComplaintRepository.existsByDiscrepancyFlag(flag)) {
            log.info("Draft complaint already exists for flag {} - skipping.", flag.getId());
            return;
        }

        // 4. Retrieve grievance contact
        String platform = user.getPlatformPreference() != null ? user.getPlatformPreference() : "Swiggy";
        PlatformGrievanceContact contact = contactRepository.findByPlatformNameIgnoreCase(platform)
                .orElseGet(() -> PlatformGrievanceContact.builder()
                        .platformName(platform)
                        .grievanceEmail("contact pending verification")
                        .verified(false)
                        .build());

        // 5. Generate text body
        String subject = String.format("Grievance Redressal Claim: Payment Discrepancy Redressal - %s", user.getName());
        String draftText = String.format(
            "To,\n" +
            "Grievance Officer,\n" +
            "%s Platform Team\n" +
            "Grievance Email: %s\n\n" +
            "Subject: Grievance Redressal Claim for Payment Discrepancies (Period: %s to %s, Bucket: %s)\n\n" +
            "Dear Grievance Officer,\n\n" +
            "I am writing to formally submit a payment grievance redressal claim under Rule 3(2) of the Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021, and Section 2(11) of the Consumer Protection Act, 2019, regarding systematic underpayments on my completed delivery orders.\n\n" +
            "Here are my account and verification details:\n" +
            "- Worker Name: %s\n" +
            "- Email: %s\n" +
            "- Flagged Audit Period: %s to %s\n" +
            "- Operating Time Window: %s\n" +
            "- Baseline Rate Checked: Rs. %.2f/km\n" +
            "- Observed Average Rate: Rs. %.2f/km\n" +
            "- Number of Matched Delivery Logs: %d\n" +
            "- Estimated Financial Shortfall: Rs. %.2f\n\n" +
            "The discrepancies have been cryptographically compiled and verified in my independent gig earnings ledger report. The corresponding tamper-evident verification audit trail PDF report is attached to this record and holds security verification hash links confirming that no entries have been edited or fabricated.\n\n" +
            "Please process this claim and credit the shortfall to my registered payout wallet within the statutory period defined under Rule 3(2) of the IT Rules 2021.\n\n" +
            "Yours sincerely,\n" +
            "%s\n" +
            "(Registered Gig Partner)",
            platform, contact.getGrievanceEmail(), flag.getPeriodStart(), flag.getPeriodEnd(), flag.getBucket(),
            user.getName(), user.getEmail(), flag.getPeriodStart(), flag.getPeriodEnd(), flag.getBucket(),
            flag.getBaselineRate().doubleValue(), flag.getObservedRate().doubleValue(), matchedTasks.size(), shortfall.doubleValue(),
            user.getName()
        );

        DraftComplaint draft = DraftComplaint.builder()
                .user(user)
                .discrepancyFlag(flag)
                .platformName(platform)
                .grievanceEmail(contact.getGrievanceEmail())
                .subject(subject)
                .draftText(draftText)
                .status("PENDING_REVIEW")
                .build();

        draftComplaintRepository.save(draft);
        log.info("Auto-generated complaint draft saved for user={}, flag={}", user.getEmail(), flag.getId());

        // Dispatch Complaint Draft Notification (Email dispatching)
        try {
            notificationDispatchService.dispatchComplaintDraft(user, draft);
        } catch (Exception e) {
            log.error("Failed to dispatch complaint draft notification for draft {}: {}", draft.getId(), e.getMessage(), e);
        }
    }

    private String classifyBucket(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        if (hour >= 12 && hour < 14) return "peak";
        if (hour >= 19 && hour < 22) return "peak";
        return "off_peak";
    }

    @Transactional(readOnly = true)
    public List<DiscrepancyFlagResponse> getFlagsForUser(User user) {
        return flagRepository.findByUserOrderByPeriodStartDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private DiscrepancyFlagResponse toResponse(DiscrepancyFlag flag) {
        return DiscrepancyFlagResponse.builder()
                .id(flag.getId())
                .periodStart(flag.getPeriodStart())
                .periodEnd(flag.getPeriodEnd())
                .bucket(flag.getBucket())
                .baselineRate(flag.getBaselineRate())
                .observedRate(flag.getObservedRate())
                .severity(flag.getSeverity())
                .sdBelow(flag.getSdBelow())
                .createdAt(flag.getCreatedAt())
                .build();
    }
}
