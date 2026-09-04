package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.report.PlanReportResponse;
import com.yr.perftest.platform.report.ReportDataService;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.plandoc.PlanAccess;
import com.yr.perftest.platform.task.plandoc.PlanDocumentService;
import com.yr.perftest.platform.task.plandoc.PlanQuickExecuteService;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService.CommentView;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService.PrecheckReport;
import com.yr.perftest.platform.task.plandoc.PrecheckSettings;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlanDocumentController {
    private final PlanDocumentService documentService;
    private final PlanWorkflowService workflowService;
    private final PlanQuickExecuteService quickExecuteService;
    private final TaskPlanService planService;
    private final ProjectAccessResolver accessResolver;
    private final ReportDataService reportDataService;

    public PlanDocumentController(PlanDocumentService documentService,
                                  PlanWorkflowService workflowService,
                                  PlanQuickExecuteService quickExecuteService,
                                  TaskPlanService planService,
                                  ProjectAccessResolver accessResolver,
                                  ReportDataService reportDataService) {
        this.documentService = documentService;
        this.workflowService = workflowService;
        this.quickExecuteService = quickExecuteService;
        this.planService = planService;
        this.accessResolver = accessResolver;
        this.reportDataService = reportDataService;
    }

    public record PlanResponse(TaskPlan plan, Map<String, Boolean> permissions) {
    }

    @GetMapping("/task-plans/{planId}")
    public PlanResponse getPlan(@PathVariable long planId) {
        TaskPlan plan = documentService.getDocument(planId);
        return new PlanResponse(plan, permissionsOf(plan));
    }

    @PutMapping("/task-plans/{planId}/document")
    public TaskPlan updateDocument(@PathVariable long planId, @RequestBody UpdateDocumentRequest request) {
        return documentService.updateMarkdown(planId, request.baseRevision(), request.markdown(), requireHuman());
    }

    @PutMapping("/task-plans/{planId}")
    public TaskPlan updateDefaultConfig(@PathVariable long planId, @RequestBody UpdatePlanConfigRequest request) {
        return planService.updatePlan(planId, request.name(), request.remark(),
                request.controllerNodeId(), request.workerNodeIds(), request.monitorTargetIds());
    }

    @DeleteMapping("/task-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable long planId) {
        TaskPlan plan = planService.getPlan(planId);
        Map<String, Boolean> permissions = permissionsOf(plan);
        if (!Boolean.TRUE.equals(permissions.get("DELETE"))) {
            throw new com.yr.perftest.platform.task.plandoc.PlanAccessDeniedException(
                    "PLAN_ACCESS_DENIED：仅负责人/项目 OWNER/系统管理员可删除计划");
        }
        planService.deletePlan(planId);
    }

    @PostMapping("/task-plans/{planId}/submit")
    public PlanResponse submit(@PathVariable long planId, @RequestBody(required = false) CommentRequest request) {
        workflowService.submit(planId, requireHuman(), request == null ? null : request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/start-review")
    public PlanResponse startReview(@PathVariable long planId) {
        workflowService.startReview(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/approve")
    public PlanResponse approve(@PathVariable long planId, @RequestBody(required = false) CommentRequest request) {
        workflowService.approve(planId, requireHuman(), request == null ? null : request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/reject")
    public PlanResponse reject(@PathVariable long planId, @RequestBody CommentRequest request) {
        workflowService.reject(planId, requireHuman(), request.comment());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/withdraw")
    public PlanResponse withdraw(@PathVariable long planId) {
        workflowService.withdraw(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/back-to-draft")
    public PlanResponse backToDraft(@PathVariable long planId) {
        workflowService.backToDraft(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/start-execution")
    public PlanResponse startExecution(@PathVariable long planId) {
        workflowService.startExecution(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/to-report")
    public PlanResponse toReport(@PathVariable long planId) {
        workflowService.toReport(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/generate-report")
    public PlanResponse generateReport(@PathVariable long planId) {
        workflowService.generateReport(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/publish")
    public PlanResponse publish(@PathVariable long planId, @RequestBody PublishRequest request) {
        workflowService.publish(planId, requireHuman(), request.conclusion());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/new-revision")
    public PlanResponse newRevision(@PathVariable long planId) {
        workflowService.newRevision(planId, requireHuman());
        return getPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/precheck-run")
    public PrecheckReport precheckRun(@PathVariable long planId) {
        return workflowService.runPrecheck(planId, true);
    }

    @PostMapping("/task-plans/{planId}/precheck-skip")
    public PlanResponse precheckSkip(@PathVariable long planId) {
        workflowService.precheckSkip(planId, requireHuman());
        return getPlan(planId);
    }

    @PutMapping("/task-plans/{planId}/precheck-settings")
    public PlanResponse precheckSettings(@PathVariable long planId, @RequestBody PrecheckSettings settings) {
        workflowService.updatePrecheckSettings(planId, requireHuman(), settings);
        return getPlan(planId);
    }

    @GetMapping("/task-plans/{planId}/comments")
    public List<CommentView> listComments(@PathVariable long planId) {
        requireHuman();
        return workflowService.listComments(planId);
    }

    @PostMapping("/task-plans/{planId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView addComment(@PathVariable long planId, @RequestBody AddCommentRequest request) {
        return workflowService.addComment(planId, requireHuman(), request.content());
    }

    @DeleteMapping("/task-plans/{planId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable long planId, @PathVariable long commentId) {
        workflowService.deleteComment(planId, commentId, requireHuman());
    }

    @GetMapping("/task-plans/{planId}/snapshots")
    public List<PlanWorkflowService.SnapshotView> listSnapshots(@PathVariable long planId) {
        return workflowService.listSnapshots(planId, requireHuman());
    }

    @GetMapping("/task-plans/{planId}/report")
    public PlanReportResponse report(@PathVariable long planId) {
        return reportDataService.aggregateByPlan(planId);
    }

    @PostMapping("/task-plans/{planId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanWorkflowService.ShareView createShare(@PathVariable long planId,
                                                     @RequestBody(required = false) ShareRequest request) {
        return workflowService.createShare(planId, requireHuman(), request == null ? null : request.expiresInDays());
    }

    @GetMapping("/task-plans/{planId}/shares")
    public List<PlanWorkflowService.ShareView> listShares(@PathVariable long planId) {
        return workflowService.listShares(planId, requireHuman());
    }

    @DeleteMapping("/task-plans/{planId}/shares/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@PathVariable long planId, @PathVariable long tokenId) {
        workflowService.revokeShare(planId, tokenId, requireHuman());
    }

    @GetMapping("/share/plans/{token}")
    public PlanWorkflowService.SharedPlanView sharedPlan(@PathVariable String token) {
        return workflowService.getSharedPlan(token);
    }

    @PostMapping("/scripts/{scriptVersionId}/quick-execute")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanQuickExecuteService.QuickExecuteResult quickExecute(@PathVariable long scriptVersionId) {
        return quickExecuteService.quickExecute(scriptVersionId, requireHuman());
    }

    @GetMapping("/projects/{projectId}/plan-templates")
    public List<com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord> listTemplates(@PathVariable long projectId) {
        requireHuman();
        return workflowService.listTemplates(projectId);
    }

    @PostMapping("/projects/{projectId}/plan-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord createTemplate(
            @PathVariable long projectId, @RequestBody TemplateRequest request) {
        return workflowService.createTemplate(projectId, requireHuman(), request.name(), request.description(), request.content());
    }

    @PutMapping("/plan-templates/{templateId}")
    public com.yr.perftest.platform.task.plandoc.PersistentPlanTemplateRecord updateTemplate(
            @PathVariable long templateId, @RequestBody TemplateRequest request) {
        return workflowService.updateTemplate(templateId, requireHuman(), request.name(), request.description(), request.content());
    }

    @DeleteMapping("/plan-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable long templateId) {
        workflowService.deleteTemplate(templateId, requireHuman());
    }

    private Map<String, Boolean> permissionsOf(TaskPlan plan) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HumanPrincipal principal = authentication != null
                && authentication.getPrincipal() instanceof HumanPrincipal human ? human : null;
        if (principal == null) {
            return Map.of();
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.projectId(), principal, plan.createdBy());
        return PlanAccess.compute(role, plan.phase(), plan.status(), workflowService.hasAnyExecution(plan.id()));
    }

    private HumanPrincipal requireHuman() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            return human;
        }
        throw new AuthenticationException("login required");
    }

    public record UpdateDocumentRequest(long baseRevision, String markdown) {
    }

    public record UpdatePlanConfigRequest(String name, String remark, Long controllerNodeId,
                                          List<Long> workerNodeIds, List<Long> monitorTargetIds) {
    }

    public record CommentRequest(String comment) {
    }

    public record AddCommentRequest(String content) {
    }

    public record PublishRequest(String conclusion) {
    }

    public record ShareRequest(Integer expiresInDays) {
    }

    public record TemplateRequest(String name, String description, String content) {
    }
}
