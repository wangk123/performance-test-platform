package com.yr.perftest.platform.report;

import com.yr.perftest.platform.api.ApiError;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Word 报告导出控制器。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private final ReportExportService reportExportService;
    private final ReportPdfService reportPdfService;

    public ReportExportController(ReportExportService reportExportService, ReportPdfService reportPdfService) {
        this.reportExportService = reportExportService;
        this.reportPdfService = reportPdfService;
    }

    @PostMapping("/plans/{planId}/export/word")
    public ResponseEntity<byte[]> exportWord(
            @PathVariable long planId,
            @RequestBody ReportExportRequest request
    ) {
        byte[] docxBytes = reportExportService.generateWord(planId, request);

        String filename = "performance-report-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docxBytes);
    }

    @PostMapping("/plans/{planId}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable long planId) {
        byte[] pdfBytes = reportPdfService.generatePdf(planId);

        String filename = "performance-report-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ExecutionValidationException ex) {
        return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ReportTemplateService.ReportTemplateException.class)
    public ResponseEntity<ApiError> handleTemplate(ReportTemplateService.ReportTemplateException ex) {
        return ResponseEntity.status(500).body(new ApiError("EXPORT_ERROR", ex.getMessage()));
    }
}
