package com.gigledger.controller;

import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.dto.TaskResponse;
import com.gigledger.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for task and payout management.
 *
 * Security:
 * - Authentication.getName() returns the email set as principal in JwtAuthFilter.
 * - This email is passed to the service which always resolves it to a User entity
 *   and scopes all queries/mutations to that user only.
 * - Ownership is verified in the service layer (throws AccessDeniedException → 403
 *   via GlobalExceptionHandler if a user tries to touch another user's task).
 *
 * Exception handling is fully delegated to GlobalExceptionHandler:
 * - NoSuchElementException   → 404
 * - AccessDeniedException    → 403
 * - IllegalStateException    → 409 (e.g. duplicate payout)
 * - MethodArgumentNotValidException → 400
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * POST /tasks — create a new task for the authenticated user.
     * Returns 201 Created with the full TaskResponse.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            Authentication auth) {
        TaskResponse response = taskService.createTask(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /tasks/{id}/payout — log the actual payout for a task.
     *
     * Security guarantees (enforced in TaskService.logPayout):
     * 1. Task must exist              → 404 if not
     * 2. Task must belong to caller   → 403 if ownership check fails
     * 3. Payout must not already exist → 409 if duplicate
     *
     * Returns 200 OK with the updated TaskResponse including payout data.
     */
    @PostMapping("/{id}/payout")
    public ResponseEntity<TaskResponse> logPayout(
            @PathVariable UUID id,
            @Valid @RequestBody PayoutRequest request,
            Authentication auth) {
        TaskResponse response = taskService.logPayout(id, request, auth.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /tasks — return all tasks for the authenticated user.
     *
     * Data isolation: findByUserOrderByAcceptedAtDesc scopes the query to the
     * authenticated user's User entity — it is structurally impossible to return
     * another user's tasks from this endpoint.
     *
     * Returns 200 OK with a (possibly empty) list of TaskResponse objects.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(Authentication auth) {
        List<TaskResponse> tasks = taskService.getUserTasks(auth.getName());
        return ResponseEntity.ok(tasks);
    }
}
