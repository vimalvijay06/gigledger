package com.gigledger.controller;

import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.dto.TaskResponse;
import com.gigledger.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST controller for task and payout management.
 *
 * Authentication.getName() returns the email we set as the principal in JwtAuthFilter.
 * This is the canonical way to get the current user in a stateless Spring Security setup.
 *
 * POST /tasks              → create a task
 * POST /tasks/{id}/payout  → log actual payout for a task
 * GET  /tasks              → list all tasks for the current user
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            Authentication auth) {
        TaskResponse response = taskService.createTask(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/payout")
    public ResponseEntity<?> logPayout(
            @PathVariable UUID id,
            @Valid @RequestBody PayoutRequest request,
            Authentication auth) {
        try {
            TaskResponse response = taskService.logPayout(id, request, auth.getName());
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            // Payout already exists
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(Authentication auth) {
        List<TaskResponse> tasks = taskService.getUserTasks(auth.getName());
        return ResponseEntity.ok(tasks);
    }
}
