package com.gigledger.service;

import com.gigledger.dto.PolicyUpdateResponse;
import com.gigledger.entity.PolicyUpdate;
import com.gigledger.repository.PolicyUpdateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyPulseService {

    private final PolicyUpdateRepository repository;
    private final WebClient              webClient;
    private final String                 mlServiceUrl;
    
    private final Path cacheDir = Paths.get("uploads", "narration");

    public PolicyPulseService(
            PolicyUpdateRepository repository,
            @Value("${ocr.service.url:http://localhost:8001}") String mlServiceUrl) {
        this.repository   = repository;
        this.mlServiceUrl = mlServiceUrl;
        this.webClient    = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        
        try {
            Files.createDirectories(cacheDir);
            log.info("Audio narration cache directory initialized: {}", cacheDir.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to initialize narration cache directory", e);
        }
    }

    // DTO records for Python service mapping
    private record RawArticle(String title, String source_url, String raw_content, String published_at, String category_hint) {}
    private record SummarizeResponse(String summary, boolean relevant) {}
    private record ClassifyResponse(String category, String urgency, String reasoning) {}
    private record TranslationResponse(String translated_text) {}

    // ── Ingestion Job ────────────────────────────────────────────────────────
    // Runs every 6 hours (21600000ms), starting 10 seconds after application startup
    @Scheduled(initialDelay = 10000, fixedDelay = 21600000)
    @Transactional
    public void runIngestionScheduler() {
        log.info("Policy Ingestion Scheduler triggered.");
        try {
            // 1. Fetch raw articles from Python service
            List<RawArticle> articles = webClient.get()
                    .uri(mlServiceUrl + "/analytics/fetch-policy")
                    .retrieve()
                    .bodyToFlux(RawArticle.class)
                    .collectList()
                    .block();

            if (articles == null || articles.isEmpty()) {
                log.info("No new policy updates retrieved.");
                return;
            }

            log.info("Ingesting {} parsed updates...", articles.size());
            int newArticles = 0;
            
            for (RawArticle art : articles) {
                // Deduplicate by URL
                if (repository.existsBySourceUrl(art.source_url())) {
                    continue;
                }

                // 2. Call Python Classification service
                ClassifyResponse classRes = null;
                try {
                    classRes = webClient.post()
                            .uri(mlServiceUrl + "/analytics/classify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("title", art.title(), "content", art.raw_content()))
                            .retrieve()
                            .bodyToMono(ClassifyResponse.class)
                            .block();
                } catch (Exception e) {
                    log.error("Failed to classify article: {}", art.title(), e);
                }

                String category = (classRes != null) ? classRes.category() : "POLICY_BENEFITS";
                String urgency = (classRes != null) ? classRes.urgency() : "NORMAL";
                String reasoning = (classRes != null) ? classRes.reasoning() : "Fallback: Classification service call failed";
                boolean isNoise = "NOISE".equalsIgnoreCase(category);

                // 3. Call Python Summarization service (skip if NOISE)
                SummarizeResponse sumRes = null;
                if (!isNoise) {
                    try {
                        sumRes = webClient.post()
                                .uri(mlServiceUrl + "/analytics/summarize")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("raw_content", art.raw_content()))
                                .retrieve()
                                .bodyToMono(SummarizeResponse.class)
                                .block();
                    } catch (Exception e) {
                        log.error("Failed to summarize article: {}", art.title(), e);
                    }
                }

                String summary = (sumRes != null) ? sumRes.summary() : "No summary available";
                if (sumRes != null && (!sumRes.relevant() || "NOT_RELEVANT".equals(summary))) {
                    isNoise = true;
                    log.info("Article '{}' marked as NOISE due to summarizer NOT_RELEVANT flag.", art.title());
                }

                // Parse ISO published timestamp
                LocalDateTime pubDate = LocalDateTime.now();
                try {
                    if (art.published_at() != null) {
                        pubDate = LocalDateTime.parse(
                                art.published_at().replace("Z", ""), 
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        );
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse published date: {}, using current time", art.published_at());
                }

                PolicyUpdate entity = PolicyUpdate.builder()
                        .title(art.title())
                        .sourceUrl(art.source_url())
                        .rawContent(art.raw_content())
                        .summary(summary)
                        .category(category)
                        .urgency(urgency)
                        .reasoning(reasoning)
                        .categoryHint(art.category_hint())
                        .publishedAt(pubDate)
                        .excluded(isNoise)
                        .build();

                repository.save(entity);
                newArticles++;
                log.info("New policy update logged: '{}' (category={}, urgency={}, excluded={})", 
                        art.title(), category, urgency, isNoise);
            }
            log.info("Policy Ingestion Complete. {} new updates added to feed.", newArticles);
        } catch (Exception e) {
            log.error("Failed to run policy ingestion pipeline", e);
        }
    }

    // ── Get Feed ─────────────────────────────────────────────────────────────
    @Transactional
    public List<PolicyUpdateResponse> getFeed(String langCode) {
        String targetLang = (langCode == null || langCode.isEmpty()) ? "en" : langCode;
        return repository.findByExcludedFalseOrderByPublishedAtDesc()
                .stream()
                .map(update -> toResponse(update, targetLang))
                .sorted((a, b) -> {
                    // 1. Sort by Urgency: URGENT before NORMAL
                    boolean aUrgent = "URGENT".equalsIgnoreCase(a.getUrgency());
                    boolean bUrgent = "URGENT".equalsIgnoreCase(b.getUrgency());
                    if (aUrgent && !bUrgent) return -1;
                    if (!aUrgent && bUrgent) return 1;

                    // 2. Group by Category
                    int catCompare = a.getCategory().compareTo(b.getCategory());
                    if (catCompare != 0) return catCompare;

                    // 3. Sort by Recency: publishedAt DESC
                    if (a.getPublishedAt() == null && b.getPublishedAt() == null) return 0;
                    if (a.getPublishedAt() == null) return 1;
                    if (b.getPublishedAt() == null) return -1;
                    return b.getPublishedAt().compareTo(a.getPublishedAt());
                })
                .collect(Collectors.toList());
    }

    private PolicyUpdateResponse toResponse(PolicyUpdate update, String langCode) {
        String title = update.getTitle();
        String summary = update.getSummary();

        if (!"en".equals(langCode)) {
            try {
                // Translate Title
                TranslationResponse transTitle = webClient.post()
                        .uri(mlServiceUrl + "/analytics/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text", update.getTitle(), "lang_code", langCode))
                        .retrieve()
                        .bodyToMono(TranslationResponse.class)
                        .block();
                if (transTitle != null && transTitle.translated_text() != null) {
                    title = transTitle.translated_text();
                }

                // Translate Summary
                TranslationResponse transSummary = webClient.post()
                        .uri(mlServiceUrl + "/analytics/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("text", update.getSummary(), "lang_code", langCode))
                        .retrieve()
                        .bodyToMono(TranslationResponse.class)
                        .block();
                if (transSummary != null && transSummary.translated_text() != null) {
                    summary = transSummary.translated_text();
                }
            } catch (Exception e) {
                log.warn("Failed to translate article id={} to lang={}, returning English", update.getId(), langCode, e);
            }
        }

        return PolicyUpdateResponse.builder()
                .id(update.getId())
                .title(title)
                .sourceUrl(update.getSourceUrl())
                .summary(summary)
                .category(update.getCategory())
                .urgency(update.getUrgency())
                .reasoning(update.getReasoning())
                .categoryHint(update.getCategoryHint())
                .publishedAt(update.getPublishedAt())
                .build();
    }

    // ── Multilingual Narration Stream & Cache ─────────────────────────────────
    @Transactional
    public byte[] getAudioNarration(UUID updateId, String langCode) {
        // Enforce supported languages
        if (!"en".equals(langCode) && !"ta".equals(langCode) && !"hi".equals(langCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language code not supported: " + langCode);
        }

        PolicyUpdate update = repository.findById(updateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy update not found: " + updateId));

        String cacheFileName = String.format("%s_%s.wav", updateId, langCode);
        Path cacheFilePath = cacheDir.resolve(cacheFileName);

        // 1. Return from cache if file exists
        if (Files.exists(cacheFilePath)) {
            log.debug("Serving cached audio narration for file={}", cacheFileName);
            try {
                return Files.readAllBytes(cacheFilePath);
            } catch (Exception e) {
                log.error("Failed to read cached audio file: {}", cacheFileName, e);
            }
        }

        // 2. Not cached: Generate new translation and audio stream
        log.info("Audio cache miss for {}. Generating translation + audio bytes...", cacheFileName);
        try {
            // A. Translate text if language is non-English
            String targetText = update.getSummary();
            if (!"en".equals(langCode)) {
                TranslationResponse transRes = webClient.post()
                        .uri(mlServiceUrl + "/analytics/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "text", update.getSummary(),
                                "lang_code", langCode
                        ))
                        .retrieve()
                        .bodyToMono(TranslationResponse.class)
                        .block();
                if (transRes != null && transRes.translated_text() != null) {
                    targetText = transRes.translated_text();
                }
            }

            // B. Request vocalization TTS from Python service (which calls Sarvam AI)
            byte[] audioBytes = webClient.post()
                    .uri(mlServiceUrl + "/voice/narrate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "text", targetText,
                            "lang_code", langCode
                    ))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("TTS service returned empty audio stream");
            }

            // C. Cache bytes to file
            Files.write(cacheFilePath, audioBytes);
            log.info("Saved generated audio narration cache: {}", cacheFileName);
            
            return audioBytes;
        } catch (Exception e) {
            log.error("Failed to compile audio narration", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compile audio narration: " + e.getMessage());
        }
    }
}
