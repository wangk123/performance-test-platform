package com.yr.perftest.platform.api;

import com.yr.perftest.platform.task.ScenarioExecution;
import com.yr.perftest.platform.task.ExecutionQueryService;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import com.yr.perftest.platform.task.ExecutionControlService;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.TaskScenario;
import com.yr.perftest.platform.task.TaskScenarioService;
import com.yr.perftest.platform.task.TestType;
import com.yr.perftest.platform.task.ThreadGroupOverrides;
import com.yr.perftest.platform.task.method.MethodSectionResponse;
import com.yr.perftest.platform.task.method.MethodSectionService;
import com.yr.perftest.platform.task.method.PlanEvidenceImageService;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.TaskSamplePage;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import com.yr.perftest.platform.monitoring.MetricKind;
import com.yr.perftest.platform.monitoring.TargetMetricsQueryResult;
import com.yr.perftest.platform.monitoring.TargetMetricsService;
import com.yr.perftest.platform.monitoring.TargetMonitoringResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskPlanController {
    private final TaskPlanService planService;
    private final TaskScenarioService scenarioService;
    private final ScenarioExecutionService executionService;
    private final ExecutionQueryService executionQueryService;
    private final ExecutionControlService executionControlService;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final TargetMetricsService targetMetricsService;
    private final PlanWorkflowService planWorkflowService;
    private final MethodSectionService methodSectionService;
    private final PlanEvidenceImageService evidenceImageService;

    public TaskPlanController(
            TaskPlanService planService,
            TaskScenarioService scenarioService,
            ScenarioExecutionService executionService,
            ExecutionQueryService executionQueryService,
            ExecutionControlService executionControlService,
            ExecutionMonitorBindingService monitorBindingService,
            TargetMetricsService targetMetricsService,
            PlanWorkflowService planWorkflowService,
            MethodSectionService methodSectionService,
            PlanEvidenceImageService evidenceImageService
    ) {
        this.planService = planService;
        this.scenarioService = scenarioService;
        this.executionService = executionService;
        this.executionQueryService = executionQueryService;
        this.executionControlService = executionControlService;
        this.monitorBindingService = monitorBindingService;
        this.targetMetricsService = targetMetricsService;
        this.planWorkflowService = planWorkflowService;
        this.methodSectionService = methodSectionService;
        this.evidenceImageService = evidenceImageService;
    }

    @PostMapping("/projects/{projectId}/task-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskPlan createPlan(
            @PathVariable long projectId,
            @Valid @RequestBody CreateTaskPlanRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String createdBy
    ) {
        String actor = createdBy;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            actor = human.username();
        }
        return planService.createPlan(projectId, request.name(), request.remark(),
                request.controllerNodeId(), request.workerNodeIds(), request.monitorTargetIds(),
                actor, request.templateId());
    }

    @GetMapping("/projects/{projectId}/task-plans")
    public List<TaskPlan> listPlans(@PathVariable long projectId) {
        return planService.listPlans(projectId);
    }

    @PostMapping("/task-plans/{planId}/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskScenario createScenario(@PathVariable long planId, @Valid @RequestBody CreateScenarioRequest request) {
        return scenarioService.createScenario(
                planId,
                request.scriptVersionId(),
                request.name(),
                request.purpose(),
                request.testType(),
                request.jmeterProperties(),
                request.threadGroupConfigs(),
                request.overridePlanDefaults() ? request.controllerNodeId() : null,
                request.overridePlanDefaults() ? request.workerNodeIds() : null,
                request.overridePlanDefaults() ? request.monitorTargetIds() : null
        );
    }

    @GetMapping("/task-plans/{planId}/scenarios")
    public List<TaskScenario> listScenarios(@PathVariable long planId) {
        return scenarioService.listScenarios(planId);
    }

    /** 测试方法章节聚合视图（前端唯一数据源）：编辑视图含 hidden 行与 hiddenCount。 */
    @GetMapping("/task-plans/{planId}/method")
    public MethodSectionResponse getPlanMethod(@PathVariable long planId) {
        planWorkflowService.requireActor(planId, currentActor(), "EDIT");
        return methodSectionService.getPlanMethod(planId);
    }

    private HumanPrincipal currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human ? human : null;
    }

    /** 补充截图上传：multipart file 必填，caption/executionId 可选，落盘 storageRoot/images/plans/{planId}。 */
    @PostMapping("/task-plans/{planId}/scenarios/{scenarioId}/evidence-images")
    @ResponseStatus(HttpStatus.CREATED)
    public MethodSectionResponse.EvidenceImage uploadEvidenceImage(
            @PathVariable long planId,
            @PathVariable long scenarioId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) String caption,
            @RequestParam("file") MultipartFile file
    ) {
        return evidenceImageService.store(planId, scenarioId, executionId, caption, file, currentActor());
    }

    /** 图注/排序修改：均可选，只更新给到的字段。 */
    @PutMapping("/images/{imageId}")
    public MethodSectionResponse.EvidenceImage updateEvidenceImage(
            @PathVariable long imageId,
            @RequestBody UpdateEvidenceImageRequest request
    ) {
        return evidenceImageService.update(imageId, currentActor(), request.caption(), request.sortOrder());
    }

    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvidenceImage(@PathVariable long imageId) {
        evidenceImageService.delete(imageId, currentActor());
    }

    /** 文件流：image→plan 反查成员权限后按存的 contentType 返回；记录或磁盘文件缺失返回 404。 */
    @GetMapping("/images/{imageId}/file")
    public ResponseEntity<byte[]> getEvidenceImageFile(@PathVariable long imageId) {
        PlanEvidenceImageService.ImageFile file = evidenceImageService.openFile(imageId, currentActor());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @ExceptionHandler(PlanEvidenceImageService.ImageNotFoundException.class)
    public ResponseEntity<ApiError> handleImageNotFound(PlanEvidenceImageService.ImageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", exception.getMessage()));
    }

    @GetMapping("/scenarios/{scenarioId}")
    public TaskScenario getScenario(@PathVariable long scenarioId) {
        return scenarioService.getScenario(scenarioId);
    }

    @PutMapping("/scenarios/{scenarioId}")
    public TaskScenario updateScenario(@PathVariable long scenarioId, @Valid @RequestBody UpdateScenarioRequest request) {
        return scenarioService.updateScenario(
                scenarioId,
                request.name(),
                request.scriptVersionId(),
                request.purpose(),
                request.testType(),
                request.jmeterProperties(),
                request.threadGroupConfigs(),
                request.controllerNodeId(),
                request.workerNodeIds(),
                request.monitorTargetIds(),
                request.overridePlanDefaults()
        );
    }

    @PostMapping("/scenarios/{scenarioId}/script")
    public TaskScenario bindScript(@PathVariable long scenarioId, @Valid @RequestBody BindScriptRequest request) {
        return scenarioService.bindScript(scenarioId, request.scriptVersionId());
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@PathVariable long scenarioId) {
        scenarioService.deleteScenario(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioExecution triggerExecution(
            @PathVariable long scenarioId,
            @RequestBody(required = false) TriggerExecutionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String executionName = request != null ? request.executionName() : null;
        Long threadGroupConfigId = request != null ? request.threadGroupConfigId() : null;
        Integer threadGroupPresetSortOrder = request != null ? request.threadGroupPresetSortOrder() : null;
        ThreadGroupOverrides overrides = request != null ? request.overrides() : null;
        ExecutionControlService.StartOutcome outcome = executionControlService.start(
                new ExecutionControlService.StartCommand(
                        scenarioId, executionName, threadGroupConfigId, threadGroupPresetSortOrder, overrides),
                idempotencyKey
        );
        return executionQueryService.getExecution(outcome.executionId());
    }

    @GetMapping("/scenarios/{scenarioId}/executions")
    public List<ScenarioExecution> listExecutions(@PathVariable long scenarioId) {
        return executionQueryService.listExecutions(scenarioId);
    }

    @GetMapping("/executions/{executionId}")
    public ScenarioExecution getExecution(@PathVariable long executionId) {
        return executionQueryService.getExecution(executionId);
    }

    @PostMapping("/executions/{executionId}/stop")
    public ScenarioExecution stopExecution(@PathVariable long executionId) {
        executionControlService.stop(executionId);
        return executionQueryService.getExecution(executionId);
    }

    /** 执行行可见性开关：只改 method_hidden，不动执行其他状态。 */
    @PatchMapping("/executions/{executionId}/method-visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setMethodVisibility(
            @PathVariable long executionId,
            @RequestBody MethodSectionService.VisibilityRequest request
    ) {
        methodSectionService.setVisibility(executionId, currentActor(), request.hidden());
    }

    @DeleteMapping("/executions/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExecution(@PathVariable long executionId) {
        executionService.deleteExecution(executionId);
    }

    @DeleteMapping("/executions/batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExecutions(@RequestBody List<Long> executionIds) {
        executionService.deleteExecutions(executionIds);
    }

    @GetMapping(value = "/executions/{executionId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLogs(@PathVariable long executionId) {
        return executionQueryService.getLogs(executionId);
    }

    @GetMapping("/executions/{executionId}/result")
    public TaskExecutionResult getResult(@PathVariable long executionId) {
        return executionQueryService.getResult(executionId);
    }

    @GetMapping("/executions/{executionId}/samples")
    public TaskSamplePage getSamples(
            @PathVariable long executionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Boolean success
    ) {
        return executionQueryService.getSamples(executionId, page, pageSize, label, code, success);
    }

    @GetMapping("/executions/{executionId}/samples/{sampleId}")
    public TaskExecutionResult.Sample getSampleDetail(
            @PathVariable long executionId,
            @PathVariable long sampleId
    ) {
        return executionQueryService.getSampleDetail(executionId, sampleId);
    }

    @GetMapping(value = "/executions/{executionId}/samples/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamSamples(
            @PathVariable long executionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return executionQueryService.streamSamples(executionId, lastEventId);
    }

    @GetMapping(value = "/executions/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamExecution(@PathVariable long executionId) {
        return executionQueryService.streamExecution(executionId);
    }

    @GetMapping("/executions/{executionId}/monitoring")
    public TaskMetricSeries getMonitoring(@PathVariable long executionId) {
        return executionQueryService.getMonitoring(executionId);
    }

    @GetMapping("/executions/{executionId}/target-monitoring")
    public TargetMonitoringResult getTargetMonitoring(@PathVariable long executionId) {
        return monitorBindingService.getExecutionMonitoring(executionId);
    }

    @GetMapping("/executions/{executionId}/target-monitoring/series")
    public TargetMetricsQueryResult getTargetMonitoringSeries(
            @PathVariable long executionId,
            @RequestParam MetricKind kind,
            @RequestParam(required = false) List<Long> targetIds,
            @RequestParam(required = false) String itemId,
            @RequestParam(required = false) Integer step
    ) {
        return targetMetricsService.querySeries(executionId, kind, targetIds, itemId, step);
    }

    public record CreateTaskPlanRequest(
            @NotBlank String name,
            String remark,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds,
            Long templateId
    ) {
    }

    public record CreateScenarioRequest(
            Long scriptVersionId,
            @NotBlank String name,
            String purpose,
            TestType testType,
            Map<String, String> jmeterProperties,
            List<ScenarioThreadGroupConfig> threadGroupConfigs,
            boolean overridePlanDefaults,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
    }

    public record UpdateScenarioRequest(
            @NotBlank String name,
            Long scriptVersionId,
            String purpose,
            TestType testType,
            Map<String, String> jmeterProperties,
            List<ScenarioThreadGroupConfig> threadGroupConfigs,
            boolean overridePlanDefaults,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
    }

    public record BindScriptRequest(
            @NotNull Long scriptVersionId
    ) {
    }

    public record TriggerExecutionRequest(
            String executionName,
            Long threadGroupConfigId,
            Integer threadGroupPresetSortOrder,
            ThreadGroupOverrides overrides
    ) {
    }

    public record UpdateEvidenceImageRequest(
            String caption,
            Integer sortOrder
    ) {
    }
}
