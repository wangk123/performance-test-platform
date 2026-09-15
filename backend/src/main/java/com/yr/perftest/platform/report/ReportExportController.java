package com.yr.perftest.platform.report;

import com.yr.perftest.platform.api.ApiError;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * 报告导出控制器：Word / PDF。导出内容含执行结果与截图字节，两端点均要求项目成员 EDIT 权限。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private static final DateTimeFormatter FILENAME_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final ReportExportService reportExportService;
    private final ReportPdfService reportPdfService;
    private final PlanWorkflowService planWorkflowService;
    private final com.yr.perftest.platform.task.PersistentTaskPlanRepository planRepository;

    public ReportExportController(
            ReportExportService reportExportService,
            ReportPdfService reportPdfService,
            PlanWorkflowService planWorkflowService,
            com.yr.perftest.platform.task.PersistentTaskPlanRepository planRepository
    ) {
        this.reportExportService = reportExportService;
        this.reportPdfService = reportPdfService;
        this.planWorkflowService = planWorkflowService;
        this.planRepository = planRepository;
    }

    @PostMapping("/plans/{planId}/export/word")
    public ResponseEntity<byte[]> exportWord(
            @PathVariable long planId,
            @RequestBody ReportExportRequest request
    ) {
        requireExistingPlan(planId);
        planWorkflowService.requireActor(planId, currentActor(), "EDIT");
        byte[] docxBytes = reportExportService.generateWord(planId, request);

        String filename = "performance-report-"
                + FILENAME_FORMAT.format(Instant.now())
                + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docxBytes);
    }

    @PostMapping("/plans/{planId}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable long planId,
            @RequestBody(required = false) ReportExportRequest request
    ) {
        requireExistingPlan(planId);
        planWorkflowService.requireActor(planId, currentActor(), "EDIT");
        byte[] pdfBytes = reportPdfService.generatePdf(planId, request);

        String filename = "performance-report-"
                + FILENAME_FORMAT.format(Instant.now())
                + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /** 缺失计划保持导出既有 404 语义（ExecutionValidationException），与 requireActor 的 400/403 语义分离。 */
    private void requireExistingPlan(long planId) {
        if (!planRepository.existsById(planId)) {
            throw new ExecutionValidationException("task plan does not exist");
        }
    }

    private HumanPrincipal currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human
                ? human
                : null;
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
