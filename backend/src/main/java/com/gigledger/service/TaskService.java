package com.gigledger.service;

import com.gigledger.dto.ConfirmTaskRequest;
import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.dto.TaskResponse;
import com.gigledger.dto.UploadScreenshotResponse;
import com.gigledger.entity.FuelCostFlag;
import com.gigledger.entity.Payout;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.FuelCostFlagRepository;
import com.gigledger.repository.PayoutRepository;
import com.gigledger.repository.TaskRepository;
import com.gigledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final PayoutRepository payoutRepository;
    private final UserRepository userRepository;
    private final OcrService ocrService;
    private final FileStorageService fileStorageService;
    private final IntegrityService integrityService;
    private final FuelPriceService fuelPriceService;
    private final FuelCostFlagRepository fuelCostFlagRepository;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found — please log in again."));
    }

    @Transactional
    public TaskResponse createTask(TaskRequest request, String userEmail) {
        User user = getUser(userEmail);

        Task task = Task.builder()
                .user(user)
                .promisedAmount(request.getPromisedAmount())
                .distanceKm(request.getDistanceKm())
                .acceptedAt(request.getAcceptedAt())
                .build();

        task = taskRepository.save(task);
        checkAndCreateFuelCostFlag(task, user);
        integrityService.appendRecord(user, "task", task.getId(), task);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse logPayout(UUID taskId, PayoutRequest request, String userEmail) {
        User user = getUser(userEmail);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));

        if (!task.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Task does not belong to the authenticated user");
        }

        if (task.getPayout() != null) {
            throw new IllegalStateException("Payout already logged for this task");
        }

        Payout payout = Payout.builder()
                .task(task)
                .actualAmount(request.getActualAmount())
                .deductionReason(request.getDeductionReason())
                .build();

        payoutRepository.save(payout);
        task.setPayout(payout);

        integrityService.appendRecord(user, "payout", payout.getId(), payout);
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getUserTasks(String userEmail) {
        User user = getUser(userEmail);
        return taskRepository.findByUserOrderByAcceptedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void checkAndCreateFuelCostFlag(Task task, User user) {
        if (task.getDistanceKm() == null || task.getDistanceKm().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String vehicle = user.getVehicleType();
        if ("bicycle".equalsIgnoreCase(vehicle) || "e-bike".equalsIgnoreCase(vehicle)) {
            return;
        }

        var cache = fuelPriceService.getTodayPetrolPrice(user.getState(), user.getDistrict());
        BigDecimal petrolPrice = cache.getPetrolPrice();

        BigDecimal efficiency = user.getFuelEfficiency();
        if (efficiency == null || efficiency.compareTo(BigDecimal.ZERO) <= 0) {
            efficiency = new BigDecimal("45.00");
        }

        BigDecimal fuelCost = task.getDistanceKm()
                .divide(efficiency, 4, java.math.RoundingMode.HALF_UP)
                .multiply(petrolPrice)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal ratio = BigDecimal.ZERO;
        if (fuelCost.compareTo(BigDecimal.ZERO) > 0) {
            ratio = task.getPromisedAmount().divide(fuelCost, 4, java.math.RoundingMode.HALF_UP);
        }

        if (fuelCost.compareTo(BigDecimal.ZERO) > 0 && ratio.compareTo(new BigDecimal("1.5")) < 0) {
            String severity = "low";
            if (ratio.compareTo(BigDecimal.ONE) < 0) {
                severity = "high";
            } else if (ratio.compareTo(new BigDecimal("1.3")) < 0) {
                severity = "medium";
            }

            fuelCostFlagRepository.deleteByTask(task);

            FuelCostFlag flag = FuelCostFlag.builder()
                    .user(user)
                    .task(task)
                    .estimatedFuelCost(fuelCost)
                    .petrolPriceUsed(petrolPrice)
                    .fuelCostRatio(ratio)
                    .severity(severity)
                    .verifiedPrice(cache.isVerified())
                    .build();

            fuelCostFlagRepository.save(flag);
            log.info("FuelCostFlag generated for task={} user={} ratio={} severity={}", 
                     task.getId(), user.getEmail(), ratio, severity);
        }
    }

    private TaskResponse toResponse(Task task) {
        Payout payout = task.getPayout();

        BigDecimal difference = (payout != null)
                ? task.getPromisedAmount().subtract(payout.getActualAmount())
                : null;

        Optional<FuelCostFlag> optFlag = fuelCostFlagRepository.findByTask(task);
        boolean flagged = optFlag.isPresent();
        BigDecimal estFuelCost = null;
        BigDecimal priceUsed = null;
        BigDecimal ratio = null;
        String severity = null;
        boolean verifiedPrice = false;

        if (flagged) {
            FuelCostFlag flag = optFlag.get();
            estFuelCost = flag.getEstimatedFuelCost();
            priceUsed = flag.getPetrolPriceUsed();
            ratio = flag.getFuelCostRatio();
            severity = flag.getSeverity();
            verifiedPrice = flag.isVerifiedPrice();
        } else {
            User user = task.getUser();
            if (task.getDistanceKm() != null && task.getDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
                String vehicle = user.getVehicleType();
                if (!"bicycle".equalsIgnoreCase(vehicle) && !"e-bike".equalsIgnoreCase(vehicle)) {
                    var cache = fuelPriceService.getTodayPetrolPrice(user.getState(), user.getDistrict());
                    priceUsed = cache.getPetrolPrice();
                    verifiedPrice = cache.isVerified();
                    BigDecimal efficiency = user.getFuelEfficiency();
                    if (efficiency == null || efficiency.compareTo(BigDecimal.ZERO) <= 0) {
                        efficiency = new BigDecimal("45.00");
                    }
                    estFuelCost = task.getDistanceKm()
                            .divide(efficiency, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(priceUsed)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    
                    if (estFuelCost.compareTo(BigDecimal.ZERO) > 0) {
                        ratio = task.getPromisedAmount().divide(estFuelCost, 4, java.math.RoundingMode.HALF_UP);
                    }
                } else {
                    estFuelCost = BigDecimal.ZERO;
                    priceUsed = BigDecimal.ZERO;
                    ratio = BigDecimal.ZERO;
                }
            }
        }

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
                .estimatedFuelCost(estFuelCost)
                .petrolPriceUsed(priceUsed)
                .fuelCostRatio(ratio)
                .fuelCostFlagged(flagged)
                .fuelCostSeverity(severity)
                .verifiedPrice(verifiedPrice)
                .build();
    }

    // ─── Phase 2 Methods ──────────────────────────────────────────────────────

    @Transactional
    public UploadScreenshotResponse uploadScreenshot(MultipartFile file, String userEmail) {
        getUser(userEmail);

        String storedPath;
        try {
            storedPath = fileStorageService.save(file);
        } catch (Exception e) {
            log.error("Failed to save screenshot for user {}: {}", userEmail, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store uploaded screenshot");
        }

        OcrService.OcrResponse ocr = ocrService.extract(file);

        return UploadScreenshotResponse.builder()
                .tempFileRef(storedPath)
                .promisedAmount(ocr.promisedAmount() != null
                        ? BigDecimal.valueOf(ocr.promisedAmount()) : null)
                .distanceKm(ocr.distanceKm() != null
                        ? BigDecimal.valueOf(ocr.distanceKm()) : null)
                .ocrConfidence(ocr.confidence())
                .rawText(ocr.rawText())
                .build();
    }

    @Transactional
    public TaskResponse confirmTask(ConfirmTaskRequest request, String userEmail) {
        User user = getUser(userEmail);

        Task task = Task.builder()
                .user(user)
                .promisedAmount(request.getPromisedAmount())
                .distanceKm(request.getDistanceKm())
                .acceptedAt(request.getAcceptedAt())
                .screenshotUrl(request.getTempFileRef())
                .ocrConfidence(BigDecimal.valueOf(request.getOcrConfidence()))
                .build();

        task = taskRepository.save(task);
        checkAndCreateFuelCostFlag(task, user);
        log.info("Task confirmed: id={} user={} amount={} confidence={}",
                task.getId(), userEmail, task.getPromisedAmount(), task.getOcrConfidence());

        integrityService.appendRecord(user, "task", task.getId(), task);
        return toResponse(task);
    }
}
