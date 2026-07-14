package com.gigledger.service;

import com.gigledger.dto.ReportDataResponse;
import com.gigledger.dto.TaskResponse;
import com.lowagie.text.*;
import com.gigledger.dto.DiscrepancyFlagResponse;

import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PdfGenerationService {

    // Theme Palette
    private static final Color COLOR_PRIMARY    = new Color(15, 17, 23);     // #0f1117 (dark bg feel)
    private static final Color COLOR_ACCENT     = new Color(249, 115, 22);   // #f97316 (orange)
    private static final Color COLOR_BORDER     = new Color(46, 50, 72);     // #2e3248
    private static final Color COLOR_SURFACE    = new Color(243, 244, 246);  // Light grey surface
    private static final Color COLOR_TEXT       = new Color(17, 24, 39);     // Dark text
    private static final Color COLOR_SUCCESS_BG = new Color(220, 252, 231);  // Green tint
    private static final Color COLOR_SUCCESS_FG = new Color(22, 101, 52);    // Dark green text
    private static final Color COLOR_DANGER_BG  = new Color(254, 226, 226);  // Red tint
    private static final Color COLOR_DANGER_FG  = new Color(153, 27, 27);    // Dark red text

    public byte[] generateVerificationReport(ReportDataResponse data) {
        log.info("Compiling PDF binary for worker={}", data.getWorkerEmail());
        Document document = new Document(PageSize.A4, 36, 36, 54, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // ── Title / Header ───────────────────────────────────────────────
            Paragraph headerTitle = new Paragraph("GIGLEDGER EARNINGS VERIFICATION REPORT", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_PRIMARY));
            headerTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(headerTitle);

            Paragraph subtitle = new Paragraph("Cryptographically Checked Pay and Rate Discrepancy Audits", 
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // ── Metadata Grid (Worker Name, Generated Date) ───────────────────
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);
            
            metaTable.addCell(createMetaCell("Worker Name:", data.getWorkerName(), FontFactory.HELVETICA_BOLD, FontFactory.HELVETICA));
            metaTable.addCell(createMetaCell("Worker Email:", data.getWorkerEmail(), FontFactory.HELVETICA_BOLD, FontFactory.HELVETICA));
            
            String generatedDate = data.getGeneratedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            metaTable.addCell(createMetaCell("Generated On:", generatedDate, FontFactory.HELVETICA_BOLD, FontFactory.HELVETICA));
            
            String dateRange = (data.getStartDate() != null && data.getEndDate() != null)
                    ? String.format("%s to %s", data.getStartDate(), data.getEndDate())
                    : "No periods flagged";
            metaTable.addCell(createMetaCell("Date Range Covered:", dateRange, FontFactory.HELVETICA_BOLD, FontFactory.HELVETICA));

            document.add(metaTable);

            // ── Cryptographic Integrity Shield ──────────────────────────────
            PdfPTable shieldTable = new PdfPTable(1);
            shieldTable.setWidthPercentage(100);
            shieldTable.setSpacingAfter(20);

            PdfPCell shieldCell = new PdfPCell();
            shieldCell.setPadding(10);
            shieldCell.setBorder(Rectangle.BOX);
            
            if (data.isIntegrityValid()) {
                shieldCell.setBackgroundColor(COLOR_SUCCESS_BG);
                shieldCell.setBorderColor(COLOR_SUCCESS_FG);
                
                Paragraph p = new Paragraph("✔ CRYPTOGRAPHIC CHECK: VERIFICATION SUCCESSFUL", 
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_SUCCESS_FG));
                p.setSpacingAfter(4);
                shieldCell.addElement(p);
                
                Paragraph pSub = new Paragraph(
                        String.format("All %d historical pay logs and discrepancy calculations have been cross-checked sequentially against their cryptographic signature chains. No records have been edited, deleted, or tampered with since creation.", 
                                data.getIntegrityTotalRecords()),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_SUCCESS_FG));
                shieldCell.addElement(pSub);
            } else {
                shieldCell.setBackgroundColor(COLOR_DANGER_BG);
                shieldCell.setBorderColor(COLOR_DANGER_FG);
                
                Paragraph p = new Paragraph("⚠ CRYPTOGRAPHIC CHECK: INTEGRITY COMPROMISED", 
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_DANGER_FG));
                p.setSpacingAfter(4);
                shieldCell.addElement(p);
                
                String brokenId = data.getIntegrityBrokenAt() != null ? data.getIntegrityBrokenAt().toString() : "Unknown";
                Paragraph pSub = new Paragraph(
                        String.format("WARNING: Verification check failed. Database records have been modified or deleted directly since their signature block was committed. Broken record link detected: %s. Report values may be modified.", 
                                brokenId),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_DANGER_FG));
                shieldCell.addElement(pSub);
            }
            
            shieldTable.addCell(shieldCell);
            document.add(shieldTable);

            // ── Overview Statistics Cards ────────────────────────────────────
            Paragraph sectionTitle = new Paragraph("SUMMARY METRICS", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY));
            sectionTitle.setSpacingAfter(10);
            document.add(sectionTitle);

            PdfPTable statsTable = new PdfPTable(3);
            statsTable.setWidthPercentage(100);
            statsTable.setSpacingAfter(25);

            statsTable.addCell(createStatCard("Total Flagged Weeks", String.valueOf(data.getTotalFlaggedPeriods()), "Underpaid rate patterns"));
            statsTable.addCell(createStatCard("Est. Earnings Lost", "₹" + data.getTotalShortfall().toPlainString(), "Shortfall during flagged weeks"));
            
            BigDecimal avgLoss = data.getTotalFlaggedPeriods() > 0
                    ? data.getTotalShortfall().divide(BigDecimal.valueOf(data.getTotalFlaggedPeriods()), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            statsTable.addCell(createStatCard("Avg Loss / Week", "₹" + avgLoss.toPlainString(), "Average shortfall per flagged period"));

            document.add(statsTable);

            // ── Flagged Discrepancy Periods Details ──────────────────────────
            Paragraph flagTitle = new Paragraph("FLAGGED ANOMALY DETAILS", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY));
            flagTitle.setSpacingAfter(10);
            document.add(flagTitle);

            if (data.getFlaggedPeriods() == null || data.getFlaggedPeriods().isEmpty()) {
                Paragraph emptyNote = new Paragraph("No sustained underpayment patterns detected in the worker's history.", 
                        FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
                emptyNote.setSpacingAfter(30);
                document.add(emptyNote);
            } else {
                for (int i = 0; i < data.getFlaggedPeriods().size(); i++) {
                    ReportDataResponse.FlaggedPeriodDetail detail = data.getFlaggedPeriods().get(i);
                    DiscrepancyFlagResponse flag = detail.getFlag();

                    // Period Header Banner
                    PdfPTable flagHeader = new PdfPTable(1);
                    flagHeader.setWidthPercentage(100);
                    
                    PdfPCell cell = new PdfPCell();
                    cell.setBackgroundColor(COLOR_PRIMARY);
                    cell.setPadding(8);
                    cell.setBorder(Rectangle.NO_BORDER);
                    
                    String titleText = String.format("DISCREPANCY ALERT #%d: %s (%s to %s)",
                            i + 1,
                            flag.getBucket().equals("peak") ? "PEAK HOURS SURGE" : "OFF-PEAK HOURS",
                            flag.getPeriodStart(),
                            flag.getPeriodEnd()
                    );
                    Paragraph pTitle = new Paragraph(titleText, 
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE));
                    cell.addElement(pTitle);
                    flagHeader.addCell(cell);
                    document.add(flagHeader);

                    // Plain text description of the underpayment anomaly
                    double pctDrop = flag.getBaselineRate().doubleValue() > 0
                            ? ((flag.getBaselineRate().doubleValue() - flag.getObservedRate().doubleValue()) / flag.getBaselineRate().doubleValue()) * 100.0
                            : 0.0;
                    
                    String descText = String.format(
                            "During this period, the worker's observed rate drops to ₹%.2f/km, representing a %.1f%% drop compared to their verified 30-day baseline rate of ₹%.2f/km. This statistical anomaly measures %.2f standard deviations below baseline, indicating a sustained underpayment pattern.",
                            flag.getObservedRate().doubleValue(),
                            pctDrop,
                            flag.getBaselineRate().doubleValue(),
                            flag.getSdBelow().doubleValue()
                    );
                    
                    Paragraph pDesc = new Paragraph(descText, 
                            FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXT));
                    pDesc.setSpacingBefore(8);
                    pDesc.setSpacingAfter(10);
                    document.add(pDesc);

                    // Table of matched tasks in that week
                    PdfPTable tasksTable = new PdfPTable(4);
                    tasksTable.setWidthPercentage(100);
                    tasksTable.setSpacingAfter(20);
                    
                    // Column widths
                    tasksTable.setWidths(new float[]{25f, 25f, 25f, 25f});
                    
                    // Table Header
                    tasksTable.addCell(createTableHeaderCell("Accepted On"));
                    tasksTable.addCell(createTableHeaderCell("Promised Fare"));
                    tasksTable.addCell(createTableHeaderCell("Actual Paid"));
                    tasksTable.addCell(createTableHeaderCell("Discrepancy"));

                    for (TaskResponse task : detail.getTasks()) {
                        String taskDate = task.getAcceptedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        tasksTable.addCell(createTableCell(taskDate));
                        tasksTable.addCell(createTableCell("₹" + task.getPromisedAmount().toPlainString()));
                        tasksTable.addCell(createTableCell("₹" + (task.getActualAmount() != null ? task.getActualAmount().toPlainString() : "Pending")));
                        
                        BigDecimal diff = task.getDifference();
                        String diffStr = (diff != null && diff.compareTo(BigDecimal.ZERO) > 0)
                                ? "▼ ₹" + diff.toPlainString()
                                : "✓ OK";
                        tasksTable.addCell(createTableCell(diffStr));
                    }
                    document.add(tasksTable);
                }
            }

            // ── Full Transaction Ledger History Section ─────────────────────
            document.add(new Paragraph("\n"));
            Paragraph ledgerTitle = new Paragraph("RECORDED TRANSACTION LEDGER HISTORY", 
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY));
            ledgerTitle.setSpacingAfter(10);
            document.add(ledgerTitle);

            if (data.getAllTasks() == null || data.getAllTasks().isEmpty()) {
                Paragraph emptyLedger = new Paragraph("No transit task logs found under this worker's profile.", 
                        FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
                emptyLedger.setSpacingAfter(20);
                document.add(emptyLedger);
            } else {
                Paragraph ledgerDesc = new Paragraph(
                        "The table below represents the full verified history of gig transits logged in this worker's ledger profile:", 
                        FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXT));
                ledgerDesc.setSpacingAfter(10);
                document.add(ledgerDesc);

                PdfPTable ledgerTable = new PdfPTable(4);
                ledgerTable.setWidthPercentage(100);
                ledgerTable.setSpacingAfter(20);
                ledgerTable.setWidths(new float[]{25f, 25f, 25f, 25f});

                ledgerTable.addCell(createTableHeaderCell("Accepted On"));
                ledgerTable.addCell(createTableHeaderCell("Promised Fare"));
                ledgerTable.addCell(createTableHeaderCell("Actual Paid"));
                ledgerTable.addCell(createTableHeaderCell("Discrepancy"));

                for (TaskResponse task : data.getAllTasks()) {
                    String taskDate = task.getAcceptedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                    ledgerTable.addCell(createTableCell(taskDate));
                    ledgerTable.addCell(createTableCell("₹" + task.getPromisedAmount().toPlainString()));
                    ledgerTable.addCell(createTableCell(task.getActualAmount() != null ? "₹" + task.getActualAmount().toPlainString() : "Pending"));
                    
                    BigDecimal diff = task.getDifference();
                    String diffStr = (diff != null && diff.compareTo(BigDecimal.ZERO) > 0)
                            ? "▼ ₹" + diff.toPlainString()
                            : "✓ OK";
                    ledgerTable.addCell(createTableCell(diffStr));
                }
                document.add(ledgerTable);
            }


            // ── Disclaimer / Footer ──────────────────────────────────────────
            Paragraph line = new Paragraph("────────────────────────────────────────────────────────────────────────", 
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY));
            line.setAlignment(Element.ALIGN_CENTER);
            line.setSpacingBefore(30);
            document.add(line);

            Paragraph disclaimer = new Paragraph(
                    "DISCLAIMER: This document is an independently compiled pay verification report generated using cryptographic ledger records from the GigLedger database. It represents a calculation of pay discrepancies based on user-submitted screenshot extractions and payout metrics. It does not constitute a legal determination of platform liability or contract breach. Users are advised to review raw logs in conjunction with platform receipts.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY));
            disclaimer.setAlignment(Element.ALIGN_CENTER);
            document.add(disclaimer);

            document.close();
        } catch (DocumentException e) {
            log.error("Failed to generate PDF document structure: {}", e.getMessage());
            throw new RuntimeException("PDF generation failed", e);
        }

        return out.toByteArray();
    }

    // ── OpenPDF Grid Helpers ──────────────────────────────────────────────────

    private PdfPCell createMetaCell(String label, String value, String labelStyle, String valueStyle) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", FontFactory.getFont(labelStyle, 9, COLOR_PRIMARY)));
        p.add(new Chunk(value, FontFactory.getFont(valueStyle, 9, COLOR_TEXT)));
        cell.addElement(p);

        return cell;
    }

    private PdfPCell createStatCard(String label, String value, String subtitle) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_SURFACE);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph pLabel = new Paragraph(label.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, COLOR_ACCENT));
        pLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pLabel);

        Paragraph pVal = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_PRIMARY));
        pVal.setAlignment(Element.ALIGN_CENTER);
        pVal.setSpacingBefore(4);
        pVal.setSpacingAfter(4);
        cell.addElement(pVal);

        Paragraph pSub = new Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY));
        pSub.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pSub);

        return cell;
    }

    private PdfPCell createTableHeaderCell(String text) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_SURFACE);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_PRIMARY));
        cell.addElement(p);
        return cell;
    }

    private PdfPCell createTableCell(String text) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);

        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXT));
        cell.addElement(p);
        return cell;
    }
}
