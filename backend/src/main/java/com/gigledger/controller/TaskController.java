package com.gigledger.controller;

import com.gigledger.dto.ConfirmTaskRequest;
import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.dto.TaskResponse;
import com.gigledger.dto.UploadScreenshotResponse;
import com.gigledger.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // Phase 1 Endpoints

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request, auth.getName()));
    }

    @PostMapping("/{id}/payout")
    public ResponseEntity<TaskResponse> logPayout(
            @PathVariable UUID id,
            @Valid @RequestBody PayoutRequest request,
            Authentication auth) {
        return ResponseEntity.ok(taskService.logPayout(id, request, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(Authentication auth) {
        return ResponseEntity.ok(taskService.getUserTasks(auth.getName()));
    }

    // Phase 2: OCR Screenshot Endpoints

    @PostMapping(value = "/upload-screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadScreenshotResponse> uploadScreenshot(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        return ResponseEntity.ok(taskService.uploadScreenshot(file, auth.getName()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<TaskResponse> confirmTask(
            @Valid @RequestBody ConfirmTaskRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.confirmTask(request, auth.getName()));
    }
}
