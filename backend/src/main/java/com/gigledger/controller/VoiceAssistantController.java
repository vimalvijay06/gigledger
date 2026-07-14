package com.gigledger.controller;

import com.gigledger.dto.PayoutRequest;
import com.gigledger.dto.TaskRequest;
import com.gigledger.entity.PolicyUpdate;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.PolicyUpdateRepository;
import com.gigledger.repository.TaskRepository;
import com.gigledger.repository.UserRepository;
import com.gigledger.service.TaskService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/voice")
public class VoiceAssistantController {

    private final UserRepository            userRepository;
    private final TaskRepository            taskRepository;
    private final PolicyUpdateRepository    policyRepository;
    private final TaskService               taskService;
    private final WebClient                 webClient;
    private final String                    mlServiceUrl;
    
    private final Path voiceCacheDir = Paths.get("uploads", "narration");

    public VoiceAssistantController(
            UserRepository userRepository,
            TaskRepository taskRepository,
            PolicyUpdateRepository policyRepository,
            TaskService taskService,
            @Value("${ocr.service.url:http://localhost:8001}") String mlServiceUrl) {
        this.userRepository   = userRepository;
        this.taskRepository   = taskRepository;
        this.policyRepository = policyRepository;
        this.taskService      = taskService;
        this.mlServiceUrl     = mlServiceUrl;
        this.webClient        = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    // Records for mapping FastAPI JSON bodies
    private record TranscribeResponse(String transcript, String language_detected, double confidence) {}
    private record ParseIntentResponse(String intent, Map<String, Object> entities) {}
    private record TranslationResponse(String translated_text) {}

    @Data
    public static class VoiceCommandResponse {
        private String transcript;
        private String textResponse;
        private String actionStatus; // SUCCESS, FAILED, NONE
        private String audioUrl;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // ── POST /voice/command ──────────────────────────────────────────────────
    @PostMapping(value = "/command", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceCommandResponse> processVoiceCommand(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "lang", defaultValue = "en") String lang,
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        User user = getUser(userEmail);
        
        long fileSizeKB = file.getSize() / 1024;
        String contentType = file.getContentType();
        java.time.LocalDateTime timestamp = java.time.LocalDateTime.now();
        log.info("DIAGNOSTIC: Received voice upload. User={}, Lang={}, Size={} KB, Content-Type='{}', Timestamp={}", 
                 userEmail, lang, fileSizeKB, contentType, timestamp);

        try {
            java.io.File uploadsDir = new java.io.File("uploads");
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }
            java.nio.file.Files.write(
                java.nio.file.Paths.get("uploads/last_voice_upload.wav"),
                file.getBytes()
            );
            log.info("DIAGNOSTIC: Saved last uploaded voice command to uploads/last_voice_upload.wav");
        } catch (Exception e) {
            log.error("DIAGNOSTIC: Failed to save last voice upload: {}", e.getMessage());
        }

        try {
            // 1. Transcribe audio using FastAPI STT
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            bodyMap.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "voice_command.wav";
                }
            });

            TranscribeResponse sttRes = webClient.post()
                    .uri(mlServiceUrl + "/voice/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyMap))
                    .retrieve()
                    .bodyToMono(TranscribeResponse.class)
                    .block();

            if (sttRes == null || "COULD_NOT_UNDERSTAND".equals(sttRes.transcript())) {
                return buildFailedResponse("I didn't catch that. Please hold the mic and speak clearly.", lang);
            }

            String transcript = sttRes.transcript();
            log.info("Voice Assistant Transcript: '{}'", transcript);

            // 2. Parse Intent using FastAPI
            ParseIntentResponse intentRes = webClient.post()
                    .uri(mlServiceUrl + "/voice/parse-intent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("transcript", transcript))
                    .retrieve()
                    .bodyToMono(ParseIntentResponse.class)
                    .block();

            if (intentRes == null) {
                return buildFailedResponse("Could not understand the parsed intent.", lang);
            }

            String intent = intentRes.intent();
            Map<String, Object> entities = intentRes.entities();
            log.info("Voice Assistant Intent: '{}' with entities={}", intent, entities);

            // 3. Action Mapping & Execution
            String templateText = "";
            String actionStatus = "NONE";

            switch (intent) {
                case "LOG_PAYOUT":
                    double payoutAmt = getDoubleEntity(entities, "amount", 0.0);
                    if (payoutAmt <= 0) {
                        templateText = "Please specify a valid payout amount.";
                        actionStatus = "FAILED";
                        break;
                    }

                    // Find latest task without a payout
                    List<Task> userTasks = taskRepository.findByUserOrderByAcceptedAtDesc(user);
                    Optional<Task> openTask = userTasks.stream().filter(t -> t.getPayout() == null).findFirst();

                    if (openTask.isEmpty()) {
                        templateText = "No active task without a payout was found. Please log a task first.";
                        actionStatus = "FAILED";
                    } else {
                        Task task = openTask.get();
                        PayoutRequest payoutReq = new PayoutRequest();
                        payoutReq.setActualAmount(BigDecimal.valueOf(payoutAmt));
                        payoutReq.setDeductionReason("Logged via GigVoice Assistant");

                        taskService.logPayout(task.getId(), payoutReq, userEmail);
                        String taskDate = task.getAcceptedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        templateText = String.format("Logged a payout of ₹%.2f for task logged on %s.", payoutAmt, taskDate);
                        actionStatus = "SUCCESS";
                    }
                    break;

                case "LOG_TASK":
                    double taskAmt = getDoubleEntity(entities, "amount", 0.0);
                    double distance = getDoubleEntity(entities, "distance", 0.0);
                    if (taskAmt <= 0) {
                        templateText = "Please specify a valid promised task amount.";
                        actionStatus = "FAILED";
                        break;
                    }

                    TaskRequest taskReq = new TaskRequest();
                    taskReq.setPromisedAmount(BigDecimal.valueOf(taskAmt));
                    taskReq.setDistanceKm(distance > 0 ? BigDecimal.valueOf(distance) : null);
                    taskReq.setAcceptedAt(LocalDateTime.now());

                    taskService.createTask(taskReq, userEmail);
                    templateText = String.format("Logged a new task with a promised fare of ₹%.2f and distance of %.1f km.", taskAmt, distance);
                    actionStatus = "SUCCESS";
                    break;

                case "CHECK_TODAY_EARNINGS":
                    List<Task> todayTasks = taskRepository.findByUserOrderByAcceptedAtDesc(user);
                    LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                    
                    BigDecimal sumToday = todayTasks.stream()
                            .filter(t -> t.getPayout() != null && t.getPayout().getLoggedAt().isAfter(startOfDay))
                            .map(t -> t.getPayout().getActualAmount())
                            .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

                    templateText = String.format("You have earned a total of ₹%.2f from payouts logged today.", sumToday.doubleValue());
                    actionStatus = "NONE";
                    break;

                case "CHECK_FAIRNESS_SCORE":
                    List<Task> allTasks = taskRepository.findByUserOrderByAcceptedAtDesc(user);
                    long totalTasks = allTasks.size();
                    long discrepantTasks = allTasks.stream()
                            .filter(t -> t.getPayout() != null && t.getPromisedAmount().compareTo(t.getPayout().getActualAmount()) > 0)
                            .count();

                    if (totalTasks == 0) {
                        templateText = "You have no tasks logged yet. Your fairness score is 100%.";
                    } else {
                        double score = ((double)(totalTasks - discrepantTasks) * 100.0) / totalTasks;
                        templateText = String.format("Your earnings fairness score is %.0f%% based on discrepancy checks. You have %d total tasks and %d underpayments.", score, totalTasks, discrepantTasks);
                    }
                    actionStatus = "NONE";
                    break;

                case "READ_LATEST_POLICY":
                    List<PolicyUpdate> policies = policyRepository.findByExcludedFalseOrderByPublishedAtDesc();
                    if (policies.isEmpty()) {
                        templateText = "There are no new government policy announcements registered in your feed.";
                    } else {
                        PolicyUpdate p = policies.get(0);
                        templateText = String.format("Latest Policy Update: %s. Summary: %s", p.getTitle(), p.getSummary());
                    }
                    actionStatus = "NONE";
                    break;

                default:
                case "UNKNOWN_INTENT":
                    templateText = "I didn't understand that command. Try saying 'log a payout' or 'how much did I earn today'.";
                    actionStatus = "FAILED";
                    break;
            }

            // 4. Translate response text if target language is not English
            String translatedText = templateText;
            if (!"en".equals(lang)) {
                TranslationResponse transRes = webClient.post()
                        .uri(mlServiceUrl + "/analytics/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text", templateText, "lang_code", lang))
                        .retrieve()
                        .bodyToMono(TranslationResponse.class)
                        .block();
                if (transRes != null && transRes.translated_text() != null) {
                    translatedText = transRes.translated_text();
                }
            }

            // 5. Generate audio response via FastAPI TTS and Cache it
            String audioCacheKey = computeHash(translatedText + "_" + lang);
            String audioFileName = String.format("voice_%s.wav", audioCacheKey);
            Path audioFilePath = voiceCacheDir.resolve(audioFileName);

            if (!Files.exists(audioFilePath)) {
                byte[] audioBytes = webClient.post()
                        .uri(mlServiceUrl + "/voice/narrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text", translatedText, "lang_code", lang))
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();
                
                if (audioBytes != null && audioBytes.length > 0) {
                    Files.write(audioFilePath, audioBytes);
                }
            }

            VoiceCommandResponse response = new VoiceCommandResponse();
            response.setTranscript(transcript);
            response.setTextResponse(translatedText);
            response.setActionStatus(actionStatus);
            response.setAudioUrl("/voice/audio/" + audioFileName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process voice assistant command", e);
            return buildFailedResponse("An internal error occurred while processing your voice command: " + e.getMessage(), lang);
        }
    }

    // Helper to stream cached voice assistant responses
    @GetMapping(value = "/audio/{filename}", produces = "audio/wav")
    public ResponseEntity<byte[]> streamCachedAudio(@PathVariable("filename") String filename) {
        Path filePath = voiceCacheDir.resolve(filename);
        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio file not found: " + filename);
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("Failed to stream audio file: {}", filename, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream audio file");
        }
    }

    private double getDoubleEntity(Map<String, Object> entities, String key, double defaultVal) {
        if (entities == null || !entities.containsKey(key)) return defaultVal;
        Object val = entities.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private ResponseEntity<VoiceCommandResponse> buildFailedResponse(String text, String lang) {
        String msg = text;
        if (!"en".equals(lang)) {
            try {
                TranslationResponse transRes = webClient.post()
                        .uri(mlServiceUrl + "/analytics/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text", text, "lang_code", lang))
                        .retrieve()
                        .bodyToMono(TranslationResponse.class)
                        .block();
                if (transRes != null && transRes.translated_text() != null) {
                    msg = transRes.translated_text();
                }
            } catch (Exception e) {
                log.warn("Failed to translate error message: {}", text);
            }
        }

        VoiceCommandResponse response = new VoiceCommandResponse();
        response.setTranscript("");
        response.setTextResponse(msg);
        response.setActionStatus("FAILED");
        response.setAudioUrl("");

        return ResponseEntity.ok(response);
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // 16 char short hash
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
