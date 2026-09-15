package com.yr.perftest.platform.task.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRecord;
import com.yr.perftest.platform.execution.aggregate.PersistentAggregateReportRepository;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.script.PersistentScriptVersionRecord;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import com.yr.perftest.platform.task.method.MethodSectionResponse.EvidenceImage;
import com.yr.perftest.platform.task.method.MethodSectionResponse.ExecutionRow;
import com.yr.perftest.platform.task.method.MethodSectionResponse.ScenarioMethodData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 测试方法章节聚合查询：场景列表、执行记录、聚合报告、截图、脚本名各一次批量查询后内存组装（无 N+1）。
 * 键名对齐：configJson = threads/rampUp/duration；summaryJson = samples/throughput/avgRt/p95/errorRate。
 */
@Service
public class MethodSectionService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentAggregateReportRepository aggregateRepository;
    private final PlanEvidenceImageRepository imageRepository;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PlanWorkflowService planWorkflowService;
    private final ObjectMapper objectMapper;

    public MethodSectionService(
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentAggregateReportRepository aggregateRepository,
            PlanEvidenceImageRepository imageRepository,
            PersistentScriptVersionRepository scriptVersionRepository,
            PlanWorkflowService planWorkflowService,
            ObjectMapper objectMapper
    ) {
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.aggregateRepository = aggregateRepository;
        this.imageRepository = imageRepository;
        this.scriptVersionRepository = scriptVersionRepository;
        this.planWorkflowService = planWorkflowService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MethodSectionResponse getPlanMethod(long planId) {
        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository
                .findAllByPlanIdOrderBySortOrderAscIdAsc(planId);
        if (scenarios.isEmpty()) {
            return new MethodSectionResponse(planId, List.of());
        }
        List<Long> scenarioIds = scenarios.stream().map(PersistentTaskScenarioRecord::getId).toList();

        List<PersistentScenarioExecutionRecord> executions = executionRepository
                .findAllByScenarioIdInOrderByIdAsc(scenarioIds);
        List<Long> executionIds = executions.stream().map(PersistentScenarioExecutionRecord::getId).toList();
        Map<Long, PersistentAggregateReportRecord> aggregateByExecutionId = executionIds.isEmpty()
                ? Map.of()
                : aggregateRepository.findByExecutionIdIn(executionIds).stream()
                        .collect(Collectors.toMap(PersistentAggregateReportRecord::getExecutionId, Function.identity()));
        Map<Long, List<PersistentScenarioExecutionRecord>> executionsByScenarioId = executions.stream()
                .collect(Collectors.groupingBy(
                        PersistentScenarioExecutionRecord::getScenarioId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<PersistentPlanEvidenceImageRecord>> imagesByScenarioId = imageRepository
                .findByScenarioIdInOrderBySortOrderAscIdAsc(scenarioIds).stream()
                .collect(Collectors.groupingBy(PersistentPlanEvidenceImageRecord::getScenarioId));

        List<Long> scriptVersionIds = scenarios.stream()
                .map(PersistentTaskScenarioRecord::getScriptVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> scriptNamesById = scriptVersionIds.isEmpty()
                ? Map.of()
                : scriptVersionRepository.findAllById(scriptVersionIds).stream()
                        .collect(Collectors.toMap(
                                PersistentScriptVersionRecord::getId,
                                PersistentScriptVersionRecord::getOriginalFilename));

        List<ScenarioMethodData> scenarioData = new ArrayList<>(scenarios.size());
        for (PersistentTaskScenarioRecord scenario : scenarios) {
            List<PersistentScenarioExecutionRecord> scenarioExecutions = executionsByScenarioId
                    .getOrDefault(scenario.getId(), List.of());
            scenarioData.add(new ScenarioMethodData(
                    scenario.getId(),
                    scenario.getName(),
                    scenario.getTestType() == null ? null : scenario.getTestType().name(),
                    scenario.getSortOrder(),
                    scenario.getScriptVersionId(),
                    scenario.getScriptVersionId() == null
                            ? null
                            : scriptNamesById.get(scenario.getScriptVersionId()),
                    scenarioExecutions.stream()
                            .map(execution -> toRow(execution, aggregateByExecutionId.get(execution.getId())))
                            .toList(),
                    scenarioExecutions.stream().filter(PersistentScenarioExecutionRecord::isMethodHidden).count(),
                    imagesByScenarioId.getOrDefault(scenario.getId(), List.of()).stream()
                            .map(MethodSectionService::toImage)
                            .toList()
            ));
        }
        return new MethodSectionResponse(planId, List.copyOf(scenarioData));
    }

    /** 行可见性开关（表格移出/恢复）：execution → scenario → plan 反查后按 EDIT 鉴权，只改 method_hidden 一个字段。 */
    @Transactional
    public void setVisibility(long executionId, HumanPrincipal actor, boolean hidden) {
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId())
                .orElseThrow(() -> new ExecutionValidationException("scenario does not exist"));
        planWorkflowService.requireActor(scenario.getPlanId(), actor, "EDIT");
        execution.setMethodHidden(hidden);
    }

    private ExecutionRow toRow(PersistentScenarioExecutionRecord execution, PersistentAggregateReportRecord aggregate) {
        JsonNode config = readTree(execution.getConfigJson());
        Metrics metrics = parseMetrics(aggregate);
        return new ExecutionRow(
                execution.getId(),
                execution.getExecutionName(),
                config.path("threads").asInt(0),
                config.path("rampUp").asInt(0),
                config.path("duration").asInt(0),
                execution.getStatus().name(),
                metrics.samples(),
                metrics.successRate(),
                metrics.avgRtMs(),
                metrics.p95Ms(),
                metrics.tps(),
                execution.getStartTime() == null
                        ? null
                        : TIME_FORMATTER.withZone(ZoneId.systemDefault()).format(execution.getStartTime()),
                execution.isMethodHidden()
        );
    }

    /** summaryJson 解析失败按无聚合处理（指标列为 null，与未终态同口径）。 */
    private Metrics parseMetrics(PersistentAggregateReportRecord aggregate) {
        if (aggregate == null) {
            return new Metrics(null, null, null, null, null);
        }
        try {
            JsonNode summary = readTree(aggregate.getSummaryJson());
            double errorRate = summary.path("errorRate").asDouble(0d);
            return new Metrics(
                    summary.path("samples").asLong(),
                    Math.round((100d - errorRate) * 10d) / 10d,
                    summary.path("avgRt").asDouble(),
                    summary.path("p95").asDouble(),
                    summary.path("throughput").asDouble()
            );
        } catch (Exception exception) {
            return new Metrics(null, null, null, null, null);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private static EvidenceImage toImage(PersistentPlanEvidenceImageRecord image) {
        return new EvidenceImage(
                image.getId(),
                image.getExecutionId(),
                image.getCaption(),
                image.getSortOrder(),
                image.getContentType(),
                image.getSizeBytes()
        );
    }

    private record Metrics(Long samples, Double successRate, Double avgRtMs, Double p95Ms, Double tps) {
    }

    /** PATCH /executions/{id}/method-visibility 请求体：只含 hidden 一个字段。 */
    public record VisibilityRequest(boolean hidden) {
    }
}
