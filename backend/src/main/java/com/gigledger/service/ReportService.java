package com.gigledger.service;

import com.gigledger.dto.DiscrepancyFlagResponse;
import com.gigledger.dto.ReportDataResponse;
import com.gigledger.dto.TaskResponse;
import com.gigledger.entity.DiscrepancyFlag;
import com.gigledger.entity.Payout;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.DiscrepancyFlagRepository;
import com.gigledger.repository.TaskRepository;
import com.gigledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository            userRepository;
    private final TaskRepository            taskRepository;
    private final DiscrepancyFlagRepository flagRepository;
    private final IntegrityService          integrityService;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found."));
    }

    private String classifyBucket(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        if (hour >= 12 && hour < 14) return "peak";
        if (hour >= 19 && hour < 22) return "peak";
        return "off_peak";
     }

    private TaskResponse toTaskResponse(Task task) {
        Payout payout = task.getPayout();
        BigDecimal difference = (payout != null)
                ? task.getPromisedAmount().subtract(payout.getActualAmount())
                : null;

        return TaskResponse.builder()
                .id(task.getId())
                .acceptedAt(task.getAcceptedAt())
                .promisedAmount(task.getPromisedAmount())
                .distanceKm(task.getDistanceKm())
                .actualAmount(payout != null ? payout.getActualAmount() : null)
                .deductionReason(payout != null ? payout.getDeductionReason() : null)
                .payoutLoggedAt(payout != null ? payout.getLoggedAt() : null)
                .difference(difference)
                .payoutLogged(payout != null)
                .build();
    }

    private DiscrepancyFlagResponse toFlagResponse(DiscrepancyFlag flag) {
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

    @Transactional(readOnly = true)
    public ReportDataResponse getReportData(String userEmail) {
        User user = getUser(userEmail);
        log.info("Generating report data aggregation for user={}", user.getEmail());

        // 1. Check Integrity Chain
        IntegrityService.VerificationResult integrity = integrityService.verifyChain(user);

        // 2. Fetch flags and tasks
        List<DiscrepancyFlag> flags = flagRepository.findByUserOrderByPeriodStartDesc(user);
        List<Task> tasks = taskRepository.findByUserOrderByAcceptedAtDesc(user);

        List<ReportDataResponse.FlaggedPeriodDetail> details = new ArrayList<>();
        BigDecimal totalShortfall = BigDecimal.ZERO;
        LocalDate earliest = null;
        LocalDate latest = null;

        // 3. Match tasks to flagged periods
        for (DiscrepancyFlag flag : flags) {
            LocalDate start = flag.getPeriodStart();
            LocalDate end = flag.getPeriodEnd();

            // Track date bounds covered
            if (earliest == null || start.isBefore(earliest)) earliest = start;
            if (latest == null || end.isAfter(latest)) latest = end;

            // Filter tasks falling into this specific week and bucket
            List<Task> matchedTasks = tasks.stream()
                    .filter(t -> {
                        LocalDate taskDate = t.getAcceptedAt().toLocalDate();
                        return (taskDate.isEqual(start) || taskDate.isAfter(start)) &&
                               (taskDate.isEqual(end) || taskDate.isBefore(end)) &&
                               flag.getBucket().equals(classifyBucket(t.getAcceptedAt()));
                    })
                    .collect(Collectors.toList());

            // Map to response models
            List<TaskResponse> taskResponses = matchedTasks.stream()
                    .map(this::toTaskResponse)
                    .collect(Collectors.toList());

            // Compute shortfall (sum of task differences)
            BigDecimal periodShortfall = matchedTasks.stream()
                    .filter(t -> t.getPayout() != null)
                    .map(t -> t.getPromisedAmount().subtract(t.getPayout().getActualAmount()))
                    .filter(diff -> diff.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

            totalShortfall = totalShortfall.add(periodShortfall);

            details.add(ReportDataResponse.FlaggedPeriodDetail.builder()
                    .flag(toFlagResponse(flag))
                    .tasks(taskResponses)
                    .build());
        }

        // 4. Map all tasks to responses for full task history print
        List<TaskResponse> allTasksMapped = tasks.stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());

        return ReportDataResponse.builder()
                .workerName(user.getName())
                .workerEmail(user.getEmail())
                .generatedAt(LocalDateTime.now())
                .integrityValid(integrity.valid())
                .integrityTotalRecords(integrity.totalRecords())
                .integrityBrokenAt(integrity.brokenAt())
                .totalFlaggedPeriods(flags.size())
                .totalShortfall(totalShortfall.setScale(2, RoundingMode.HALF_UP))
                .startDate(earliest)
                .endDate(latest)
                .flaggedPeriods(details)
                .allTasks(allTasksMapped)
                .build();
    }
}
