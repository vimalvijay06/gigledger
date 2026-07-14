package com.gigledger.controller;

import com.gigledger.dto.ReportDataResponse;
import com.gigledger.service.PdfGenerationService;
import com.gigledger.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfGenerationService pdfService;

    // ── GET /reports/data ────────────────────────────────────────────────────
    @GetMapping("/data")
    public ResponseEntity<ReportDataResponse> getReportData(Authentication auth) {
        log.info("Report data payload requested for user={}", auth.getName());
        return ResponseEntity.ok(reportService.getReportData(auth.getName()));
    }

    // ── GET /reports/generate ────────────────────────────────────────────────
    // Compiles aggregated report data and streams a print-ready PDF binary download.
    @GetMapping("/generate")
    public ResponseEntity<byte[]> downloadReport(Authentication auth) {
        log.info("PDF download triggered for user={}", auth.getName());
        ReportDataResponse reportData = reportService.getReportData(auth.getName());
        
        byte[] pdfBytes = pdfService.generateVerificationReport(reportData);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gigledger-earnings-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
