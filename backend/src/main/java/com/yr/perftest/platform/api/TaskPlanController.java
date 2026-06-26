package com.yr.perftest.platform.api;

import com.yr.perftest.platform.task.ScenarioExecution;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import com.yr.perftest.platform.task.TaskScenario;
import com.yr.perftest.platform.task.TaskScenarioService;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.TaskMetricSeries;
import com.yr.perftest.platform.execution.TaskSamplePage;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskPlanController {
    private final TaskPlanService planService;
    private final TaskScenarioService scenarioService;
    private final ScenarioExecutionService executionService;
    private final ExecutionMonitorBindingService monitorBindingService;
    private final TargetMetricsService targetMetricsService;

    public TaskPlanController(
            TaskPlanService planService,
            TaskScenarioService scenarioService,
            ScenarioExecutionService executionService,
            ExecutionMonitorBindingService monitorBindingService,
            TargetMetricsService targetMetricsService
    ) {
        this.planService = planService;
        this.scenarioService = scenarioService;
        this.executionService = executionService;
        this.monitorBindingService = monitorBindingService;
        this.targetMetricsService = targetMetricsService;
    }

    @PostMapping("/projects/{projectId}/task-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskPlan createPlan(
            @PathVariable long projectId,
            @Valid @RequestBody CreateTaskPlanRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String createdBy
    ) {
        return planService.createPlan(
                projectId,
                request.name(),
                request.remark(),
                request.controllerNodeId(),
                request.workerNodeIds(),
                request.monitorTargetIds(),
                createdBy
        );
    }

    @GetMapping("/projects/{projectId}/task-plans")
    public List<TaskPlan> listPlans(@PathVariable long projectId) {
        return planService.listPlans(projectId);
    }

    @GetMapping("/task-plans/{planId}")
    public TaskPlan getPlan(@PathVariable long planId) {
        return planService.getPlan(planId);
    }

    @PutMapping("/task-plans/{planId}")
    public TaskPlan updatePlan(@PathVariable long planId, @Valid @RequestBody UpdateTaskPlanRequest request) {
        return planService.updatePlan(
                planId,
                request.name(),
                request.remark(),
                request.controllerNodeId(),
                request.workerNodeIds(),
                request.monitorTargetIds()
        );
    }

    @DeleteMapping("/task-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable long planId) {
        planService.deletePlan(planId);
    }

    @PostMapping("/task-plans/{planId}/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskScenario createScenario(@PathVariable long planId, @Valid @RequestBody CreateScenarioRequest request) {
        return scenarioService.createScenario(
                planId,
                request.scriptVersionId(),
                request.name(),
                request.threads() == null ? 1 : request.threads(),
                request.rampUp() == null ? 0 : request.rampUp(),
                request.duration() == null ? 0 : request.duration(),
                request.loops() == null ? 1 : request.loops(),
                request.jmeterProperties(),
                request.overridePlanDefaults() ? request.controllerNodeId() : null,
                request.overridePlanDefaults() ? request.workerNodeIds() : null,
                request.overridePlanDefaults() ? request.monitorTargetIds() : null
        );
    }

    @GetMapping("/task-plans/{planId}/scenarios")
    public List<TaskScenario> listScenarios(@PathVariable long planId) {
        return scenarioService.listScenarios(planId);
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
                request.threads() == null ? 1 : request.threads(),
                request.rampUp() == null ? 0 : request.rampUp(),
                request.duration() == null ? 0 : request.duration(),
                request.loops() == null ? 1 : request.loops(),
                request.jmeterProperties(),
                request.controllerNodeId(),
                request.workerNodeIds(),
                request.monitorTargetIds(),
                request.overridePlanDefaults()
        );
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@PathVariable long scenarioId) {
        scenarioService.deleteScenario(scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioExecution triggerExecution(@PathVariable long scenarioId) {
        return executionService.triggerExecution(scenarioId);
    }

    @GetMapping("/scenarios/{scenarioId}/executions")
    public List<ScenarioExecution> listExecutions(@PathVariable long scenarioId) {
        return executionService.listExecutions(scenarioId);
    }

    @GetMapping("/executions/{executionId}")
    public ScenarioExecution getExecution(@PathVariable long executionId) {
        return executionService.getExecution(executionId);
    }

    @PostMapping("/executions/{executionId}/stop")
    public ScenarioExecution stopExecution(@PathVariable long executionId) {
        executionService.stopExecution(executionId);
        return executionService.getExecution(executionId);
    }

    @DeleteMapping("/executions/{executionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExecution(@PathVariable long executionId) {
        executionService.deleteExecution(executionId);
    }

    @GetMapping(value = "/executions/{executionId}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLogs(@PathVariable long executionId) {
        return executionService.getLogs(executionId);
    }

    @GetMapping("/executions/{executionId}/result")
    public TaskExecutionResult getResult(@PathVariable long executionId) {
        return executionService.getResult(executionId);
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
        return executionService.getSamples(executionId, page, pageSize, label, code, success);
    }

    @GetMapping("/executions/{executionId}/samples/{sampleId}")
    public TaskExecutionResult.Sample getSampleDetail(
            @PathVariable long executionId,
            @PathVariable long sampleId
    ) {
        return executionService.getSampleDetail(executionId, sampleId);
    }

    @GetMapping(value = "/executions/{executionId}/samples/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamSamples(
            @PathVariable long executionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return executionService.streamSamples(executionId, lastEventId);
    }

    @GetMapping(value = "/executions/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamExecution(@PathVariable long executionId) {
        return executionService.streamExecution(executionId);
    }

    @GetMapping("/executions/{executionId}/monitoring")
    public TaskMetricSeries getMonitoring(@PathVariable long executionId) {
        return executionService.getMonitoring(executionId);
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
            List<Long> monitorTargetIds
    ) {
    }

    public record UpdateTaskPlanRequest(
            @NotBlank String name,
            String remark,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
    }

    public record CreateScenarioRequest(
            @NotNull Long scriptVersionId,
            @NotBlank String name,
            Integer threads,
            Integer rampUp,
            Integer duration,
            Integer loops,
            Map<String, String> jmeterProperties,
            boolean overridePlanDefaults,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
    }

    public record UpdateScenarioRequest(
            @NotBlank String name,
            Long scriptVersionId,
            Integer threads,
            Integer rampUp,
            Integer duration,
            Integer loops,
            Map<String, String> jmeterProperties,
            boolean overridePlanDefaults,
            Long controllerNodeId,
            List<Long> workerNodeIds,
            List<Long> monitorTargetIds
    ) {
    }
}
