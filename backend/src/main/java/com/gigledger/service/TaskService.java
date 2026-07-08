package com.gigledger.service;

import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.dto.TaskResponse;
import com.gigledger.entity.Payout;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.PayoutRepository;
import com.gigledger.repository.TaskRepository;
import com.gigledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for task creation, payout logging, and retrieval.
 *
 * Security rule: a user can only see and modify their own tasks.
 * We enforce this by always scoping queries to the authenticated user's entity,
 * never trusting a raw task ID without verifying ownership.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final PayoutRepository payoutRepository;
    private final UserRepository userRepository;

    /**
     * Look up the User entity for the authenticated email.
     * This is a helper used by all methods below.
     */
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
    }

    /**
     * Create a new task for the authenticated user.
     */
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
        return toResponse(task);
    }

    /**
     * Log the actual payout for an existing task.
     *
     * Validates:
     * 1. The task exists
     * 2. The task belongs to the authenticated user (ownership check)
     * 3. A payout hasn't already been logged (idempotency guard)
     */
    @Transactional
    public TaskResponse logPayout(UUID taskId, PayoutRequest request, String userEmail) {
        User user = getUser(userEmail);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));

        // Ownership check — prevent one user from logging payouts on another user's tasks
        if (!task.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Task does not belong to the authenticated user");
        }

        // Idempotency guard — don't allow overwriting an existing payout
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

        return toResponse(task);
    }

    /**
     * Return all tasks (with their linked payouts) for the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> getUserTasks(String userEmail) {
        User user = getUser(userEmail);
        return taskRepository.findByUserOrderByAcceptedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Map a Task entity (plus its optional Payout) to a flat DTO.
     * The difference (promised - actual) is computed here so the frontend
     * doesn't need to do arithmetic.
     */
    private TaskResponse toResponse(Task task) {
        Payout payout = task.getPayout();

        // Compute difference only when a payout exists (promised - actual).
        // Positive = underpaid, Zero = exact, Negative = overpaid.
        BigDecimal difference = (payout != null)
                ? task.getPromisedAmount().subtract(payout.getActualAmount())
                : null;

        return TaskResponse.builder()
                .id(task.getId())
                .acceptedAt(task.getAcceptedAt())
                .promisedAmount(task.getPromisedAmount())
                .distanceKm(task.getDistanceKm())
                // All payout fields use direct null-guard so the IDE's flow analysis is happy
                .actualAmount(payout != null ? payout.getActualAmount() : null)
                .deductionReason(payout != null ? payout.getDeductionReason() : null)
                .payoutLoggedAt(payout != null ? payout.getLoggedAt() : null)
                .difference(difference)
                .payoutLogged(payout != null)
                .build();
    }
}
