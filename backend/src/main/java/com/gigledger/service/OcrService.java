package com.gigledger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service
public class OcrService {

    public record OcrResponse(
            Double  promisedAmount,   // nullable — null if OCR couldn't find a fare
            Double  distanceKm,       // nullable — null if OCR couldn't find a distance
            double  confidence,       // 0.0–1.0
            String  rawText           // full text Tesseract extracted (for debugging)
    ) {}

    private final WebClient webClient;

    public OcrService(@Value("${ocr.service.url}") String ocrServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(ocrServiceUrl)
                // 30 second timeout — OCR on a low-res image can be slow
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10 MB
                .build();
        log.info("OcrService configured to call: {}", ocrServiceUrl);
    }

    public OcrResponse extract(MultipartFile file) {
        log.info("Forwarding screenshot to OCR service: {} ({} KB)",
                file.getOriginalFilename(), file.getSize() / 1024);

        try {
            // Build multipart body for the outgoing request to Python FastAPI.
            // FastAPI expects the file in a field named "file".
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file",
                    new ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            // Provide the original filename so FastAPI can check the extension
                            return Optional.ofNullable(file.getOriginalFilename()).orElse("screenshot.png");
                        }
                    },
                    MediaType.parseMediaType(
                            Optional.ofNullable(file.getContentType()).orElse("image/png")
                    )
            );

            // Make the POST call — .block() waits synchronously for the response.
            // This is correct in a Spring MVC (non-reactive) controller.
            PythonOcrResponse raw = webClient.post()
                    .uri("/ocr/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(PythonOcrResponse.class)
                    .block();

            if (raw == null) {
                throw new OcrCallException("OCR service returned an empty response");
            }

            log.info("OCR result: amount={} distance={} confidence={}",
                    raw.promised_amount(), raw.distance_km(), raw.confidence());

            return new OcrResponse(raw.promised_amount(), raw.distance_km(), raw.confidence(), raw.raw_text());

        } catch (WebClientResponseException e) {
            log.error("OCR service responded with error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new OcrCallException("OCR service error " + e.getStatusCode() + ": " + e.getMessage());
        } catch (OcrCallException e) {
            throw e;  // re-throw our own exceptions as-is
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            throw new OcrCallException("Could not read uploaded image: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling OCR service", e);
            throw new OcrCallException("Could not reach OCR service — is it running? " + e.getMessage());
        }
    }

    private record PythonOcrResponse(
            Double  promised_amount,
            Double  distance_km,
            double  confidence,
            String  raw_text
    ) {}

    public static class OcrCallException extends RuntimeException {
        public OcrCallException(String message) {
            super(message);
        }
    }
}
