package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.task.ExecutionQueryService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * 判等引擎（spec §4）：输入 = 冻结的文档指标章节（P0-1 阶段规则保证）+ 每场景最近执行，
 * 即时重算、不持久化（V8）。场景级绑定 Summary（TPS/AVG_RT/P95/ERROR_RATE），
 * 交易级绑定 AggregateRow（含 P99）；不可判类型落无法判定并附原因。
 */
@Service
public class PlanVerdictService {

    public enum VerdictStatus { ACHIEVED, MISSED, INDETERMINATE }

    public record VerdictRow(
            String objectName, String metricRaw, PlanAcceptanceParser.MetricType metricType,
            String targetRaw, Double targetValue, String actualValue, VerdictStatus status,
            String reason, Long scenarioId, Long executionId) {
    }

    /** overall ∈ PASSED|FAILED|INDETERMINATE|NONE（NONE = 无指标或不可用）。 */
    public record VerdictResult(boolean present, String overall, String prefillConclusion, List<VerdictRow> rows) {
        static final VerdictResult NONE = new VerdictResult(false, "NONE", null, List.of());
    }

    public record VerdictRowView(
            String objectName, String metricRaw, String metricType, String targetRaw,
            Double targetValue, String actualValue, String status, String reason,
            Long scenarioId, Long executionId) {
    }

    public record VerdictView(
            boolean present, boolean available, String overall,
            String prefillConclusion, List<VerdictRowView> rows) {
    }

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final ExecutionQueryService executionQueryService;

    public PlanVerdictService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            ExecutionQueryService executionQueryService
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.executionQueryService = executionQueryService;
    }

    @Transactional(readOnly = true)
    public VerdictResult compute(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return VerdictResult.NONE;
        }
        PlanAcceptanceParser.AcceptanceSection acceptance =
                PlanAcceptanceParser.parseLeniently(plan.getBody() == null ? "" : plan.getBody());
        if (!acceptance.present()) {
            return VerdictResult.NONE;
        }
        List<PersistentTaskScenarioRecord> scenarios =
                scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);
        List<ScenarioData> data = new ArrayList<>();
        for (PersistentTaskScenarioRecord scenario : scenarios) {
            PersistentScenarioExecutionRecord latest =
                    executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).orElse(null);
            TaskExecutionResult result = latest == null ? null : executionQueryService.getResult(latest.getId());
            data.add(new ScenarioData(scenario.getId(), scenario.getName(), latest, result));
        }
        List<VerdictRow> rows = acceptance.rows().stream()
                .map(metric -> judge(metric, data))
                .toList();
        return new VerdictResult(true, overallOf(rows), prefillText(rows), rows);
    }

    @Transactional(readOnly = true)
    public VerdictView view(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            throw new PlanValidationException("PLAN_INVALID：task plan does not exist");
        }
        // 执行全部完成即可判读（发布预填依赖，报告阶段已取消手动生成）；REPORT·DONE 兼容存量；
        // 复测重置（回 RUNNING）/新修订后返回未生成（spec §6.1），杜绝"接口算新执行、文档达成表还是旧的"
        boolean available = (plan.getPhase() == PlanPhase.EXECUTION || plan.getPhase() == PlanPhase.REPORT)
                && plan.getStatus() == PlanStatus.DONE
                || (plan.getPhase() == PlanPhase.PUBLISH && plan.getStatus() == PlanStatus.PUBLISHED);
        if (!available) {
            // 复测重置后返回未生成（spec §6.1），杜绝"接口算新执行、文档达成表还是旧的"
            PlanAcceptanceParser.AcceptanceSection acceptance =
                    PlanAcceptanceParser.parseLeniently(plan.getBody() == null ? "" : plan.getBody());
            return new VerdictView(acceptance.present(), false, "NONE", null, List.of());
        }
        VerdictResult result = compute(planId);
        return new VerdictView(result.present(), true, result.overall(), result.prefillConclusion(),
                result.rows().stream().map(r -> new VerdictRowView(
                        r.objectName(), r.metricRaw(), r.metricType().name(), r.targetRaw(),
                        r.targetValue(), r.actualValue(), r.status().name(), r.reason(),
                        r.scenarioId(), r.executionId())).toList());
    }

    private VerdictRow judge(PlanAcceptanceParser.AcceptanceMetricRow metric, List<ScenarioData> data) {
        // 1. 精确匹配场景名（spec §4.2）
        ScenarioData scenarioHit = data.stream()
                .filter(d -> d.scenarioName().equals(metric.objectName()))
                .findFirst().orElse(null);
        if (scenarioHit != null) {
            return judgeScenarioLevel(metric, scenarioHit);
        }
        // 2. 精确匹配交易名 label
        List<ScenarioData> labelHits = data.stream()
                .filter(d -> d.result() != null && d.result().aggregateRows() != null
                        && d.result().aggregateRows().stream()
                        .anyMatch(r -> r.label().equals(metric.objectName())))
                .toList();
        if (labelHits.size() > 1) {
            return indeterminate(metric, null, null,
                    "同名交易出现在多个场景，请改用场景名对象或拆分场景");
        }
        if (labelHits.isEmpty()) {
            return indeterminate(metric, null, null, "未匹配对象（对象需为场景名或交易名）");
        }
        ScenarioData hit = labelHits.get(0);
        if (hit.execution() == null) {
            return indeterminate(metric, hit.scenarioId(), null, "场景无执行");
        }
        return judgeTransactionLevel(metric, hit);
    }

    private VerdictRow judgeScenarioLevel(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit) {
        if (hit.execution() == null || hit.result() == null) {
            return indeterminate(metric, hit.scenarioId(), null, "场景无执行");
        }
        TaskExecutionResult.Summary summary = hit.result().summary();
        // getResult 对无持久化快照的终态执行兜底返回全零 Summary（samples=0），按 0 值判等会产生伪 MISSED/伪 ACHIEVED，
        // 零样本必须落无法判定（spec §3.4/§4.2：判不了就明说）
        if (summary == null || summary.samples() <= 0) {
            return indeterminate(metric, hit.scenarioId(), hit.execution().getId(), "场景最近执行无聚合数据，无法判定");
        }
        return switch (metric.metricType()) {
            case TPS -> compare(metric, hit, summary.throughput(), summary.throughput() >= metric.targetValue(),
                    String.format(Locale.ROOT, "%.1f", summary.throughput()), "取自场景最近执行");
            case AVG_RT -> compare(metric, hit, (double) summary.avgRt(), summary.avgRt() <= metric.targetValue(),
                    summary.avgRt() + "ms", "取自场景最近执行");
            case P95 -> compare(metric, hit, (double) summary.p95(), summary.p95() <= metric.targetValue(),
                    summary.p95() + "ms", "取自场景最近执行");
            case ERROR_RATE -> compare(metric, hit, summary.errorRate(), summary.errorRate() <= metric.targetValue(),
                    String.format(Locale.ROOT, "%.2f%%", summary.errorRate()), "取自场景最近执行");
            case P99 -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "场景级无 P99 数据，请改用交易级对象");
            default -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "该指标当前不可自动判定，需人工评估");
        };
    }

    private VerdictRow judgeTransactionLevel(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit) {
        TaskExecutionResult.AggregateRow row = hit.result().aggregateRows().stream()
                .filter(r -> r.label().equals(metric.objectName()))
                .findFirst().orElseThrow();
        return switch (metric.metricType()) {
            case TPS -> compare(metric, hit, row.throughput(), row.throughput() >= metric.targetValue(),
                    String.format(Locale.ROOT, "%.1f", row.throughput()), "取自最近执行 label 汇总");
            case AVG_RT -> compare(metric, hit, (double) row.average(), row.average() <= metric.targetValue(),
                    row.average() + "ms", "取自最近执行 label 汇总");
            case P95 -> compare(metric, hit, (double) row.p95(), row.p95() <= metric.targetValue(),
                    row.p95() + "ms", "取自最近执行 label 汇总");
            case P99 -> compare(metric, hit, (double) row.p99(), row.p99() <= metric.targetValue(),
                    row.p99() + "ms", "取自最近执行 label 汇总");
            case ERROR_RATE -> compare(metric, hit, row.errorRate(), row.errorRate() <= metric.targetValue(),
                    String.format(Locale.ROOT, "%.2f%%", row.errorRate()), "取自最近执行 label 汇总");
            default -> indeterminate(metric, hit.scenarioId(), hit.execution().getId(),
                    "该指标当前不可自动判定，需人工评估");
        };
    }

    private VerdictRow compare(PlanAcceptanceParser.AcceptanceMetricRow metric, ScenarioData hit,
                               double actual, boolean achieved, String actualText, String note) {
        return new VerdictRow(metric.objectName(), metric.metricRaw(), metric.metricType(),
                metric.targetRaw(), metric.targetValue(), actualText,
                achieved ? VerdictStatus.ACHIEVED : VerdictStatus.MISSED,
                note, hit.scenarioId(), hit.execution().getId());
    }

    private VerdictRow indeterminate(PlanAcceptanceParser.AcceptanceMetricRow metric,
                                     Long scenarioId, Long executionId, String reason) {
        return new VerdictRow(metric.objectName(), metric.metricRaw(), metric.metricType(),
                metric.targetRaw(), metric.targetValue(), "—", VerdictStatus.INDETERMINATE,
                reason, scenarioId, executionId);
    }

    private String overallOf(List<VerdictRow> rows) {
        boolean anyMissed = rows.stream().anyMatch(r -> r.status() == VerdictStatus.MISSED);
        if (anyMissed) {
            return "FAILED";
        }
        return rows.stream().allMatch(r -> r.status() == VerdictStatus.ACHIEVED) ? "PASSED" : "INDETERMINATE";
    }

    /** 自动判定文本：达成表尾行与发布预填共用（spec §5/§6.1）。 */
    private String prefillText(List<VerdictRow> rows) {
        long missed = rows.stream().filter(r -> r.status() == VerdictStatus.MISSED).count();
        long indeterminate = rows.stream().filter(r -> r.status() == VerdictStatus.INDETERMINATE).count();
        String needHuman = indeterminate > 0 ? "、" + indeterminate + " 项需人工评估" : "";
        if (missed > 0) {
            StringJoiner joiner = new StringJoiner("、");
            rows.stream().filter(r -> r.status() == VerdictStatus.MISSED)
                    .forEach(r -> joiner.add(r.objectName() + " " + r.metricRaw()));
            return "自动判定：未达成（" + missed + " 项未达标" + needHuman + "）——" + joiner;
        }
        if (indeterminate > 0) {
            return "自动判定：无法判定（部分需人工评估）（" + (rows.size() - indeterminate) + " 项达标、"
                    + indeterminate + " 项需人工评估）";
        }
        return "自动判定：达成（" + rows.size() + " 项全部达标）";
    }

    private record ScenarioData(
            Long scenarioId, String scenarioName,
            PersistentScenarioExecutionRecord execution, TaskExecutionResult result) {
    }
}
