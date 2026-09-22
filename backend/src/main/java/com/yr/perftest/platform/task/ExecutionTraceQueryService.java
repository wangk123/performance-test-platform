package com.yr.perftest.platform.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceKind;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceProperties;
import com.yr.perftest.platform.evidence.deep.SkyWalkingGraphqlClient;
import com.yr.perftest.platform.evidence.deep.SkyWalkingTraceModels;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 执行 trace 查询服务：终态快照职责（capture/load/delete，Task 6）+
 * 执行详情链路面板查询（queryTraces/queryTraceDetail，Task 4，终态快照优先、OAP 直连兜底）。
 */
@Service
public class ExecutionTraceQueryService {
    /** 终态快照按耗时取 Top500（与链路面板快照回看约定一致）。 */
    static final int SNAPSHOT_LIMIT = 500;

    /** 终态判定：进入这些状态后 trace 列表只认快照（有快照行即出，含 total=0）。 */
    private static final Set<ExecutionStatus> TERMINAL_STATUSES = Set.of(
            ExecutionStatus.SUCCESS, ExecutionStatus.FAILED,
            ExecutionStatus.INTERRUPTED, ExecutionStatus.CANCELLED);

    private final PersistentExecutionTraceSnapshotRepository repository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final SkyWalkingGraphqlClient client;
    private final DeepEvidenceProperties properties;
    private final ObjectMapper objectMapper;

    public ExecutionTraceQueryService(
            PersistentExecutionTraceSnapshotRepository repository,
            PersistentScenarioExecutionRepository executionRepository,
            SkyWalkingGraphqlClient client,
            DeepEvidenceProperties properties,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行终态收尾拉 trace 摘要快照。OAP 失败静默——终态收尾不得因快照失败中断
     * （对齐 {@code TargetMetricsSnapshotService#captureAll}）。
     */
    @Transactional
    public void captureSnapshot(long executionId) {
        String oap = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
        if (oap == null || oap.isBlank()) {
            return;
        }
        try {
            PersistentScenarioExecutionRecord execution = loadExecution(executionId).orElse(null);
            if (execution == null || execution.getStartTime() == null) {
                return;
            }
            Instant to = execution.getEndTime() == null ? Instant.now() : execution.getEndTime();
            List<SkyWalkingTraceModels.TraceBrief> briefs = client.queryBasicTraces(
                    null, null, execution.getStartTime(), to, null, null, true, 1, SNAPSHOT_LIMIT).traces();
            repository.deleteByExecutionId(executionId);
            repository.flush(); // UNIQUE(execution_id) 下必须先落 DELETE 再 INSERT（Hibernate 默认 insert 先于 delete flush）
            repository.save(new PersistentExecutionTraceSnapshotRecord(
                    executionId,
                    objectMapper.writeValueAsString(briefs),
                    briefs.size(),
                    Instant.now()));
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public Optional<PersistentExecutionTraceSnapshotRecord> loadSnapshot(long executionId) {
        return repository.findByExecutionId(executionId);
    }

    @Transactional
    public void deleteByExecutionId(long executionId) {
        repository.deleteByExecutionId(executionId);
    }

    /**
     * 执行详情链路面板列表（Task 4）：终态且快照行存在（含 total=0 空快照）一律从快照出，
     * 不回落 OAP；否则 OAP 直连（分页/过滤下推），OAP 不可达返回 available=false 而非 5xx。
     */
    @Transactional(readOnly = true)
    public ExecutionTraceViews.ExecutionTracesPageView queryTraces(
            long executionId, String service, String endpoint,
            Boolean onlyError, Long minDurationMs, String sort, int page, int size) {
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 1);
        PersistentScenarioExecutionRecord execution = requireExecution(executionId);
        Optional<PersistentExecutionTraceSnapshotRecord> snapshot = repository.findByExecutionId(executionId);
        if (TERMINAL_STATUSES.contains(execution.getStatus()) && snapshot.isPresent()) {
            return pageFromBriefs(parseSnapshot(snapshot.get().getTracesJson()),
                    service, endpoint, onlyError, minDurationMs, sort, page, size);
        }
        String oap = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
        if (oap == null || oap.isBlank()) {
            return unavailablePage(page, size, "unconfigured");
        }
        try {
            Instant from = execution.getStartTime() != null ? execution.getStartTime() : execution.getCreatedAt();
            Instant to = execution.getEndTime() != null ? execution.getEndTime() : Instant.now();
            SkyWalkingTraceModels.TraceBriefsPage result = client.queryBasicTraces(
                    service, endpoint, from, to, minDurationMs, onlyError,
                    !"time".equals(sort), page, size);
            return new ExecutionTraceViews.ExecutionTracesPageView(
                    true, null,
                    result.traces().stream().map(ExecutionTraceViews::listItem).toList(),
                    result.total(), page, size);
        } catch (RuntimeException exception) {
            return unavailablePage(page, size, "source-unavailable");
        }
    }

    /**
     * 单条 trace 详情：span 数据只有 OAP 有（快照仅存摘要），详情始终查 OAP。
     * 快照存在但 OAP span 查询失败 → retention-expired（摘要还在、明细已过期）。
     */
    @Transactional(readOnly = true)
    public ExecutionTraceViews.ExecutionTraceDetailView queryTraceDetail(long executionId, String traceId) {
        requireExecution(executionId);
        String oap = properties.forKind(DeepEvidenceKind.TRACE).getEndpoint();
        if (oap == null || oap.isBlank()) {
            return new ExecutionTraceViews.ExecutionTraceDetailView(false, "unconfigured", null);
        }
        try {
            SkyWalkingTraceModels.TraceDetail detail = client.queryTrace(traceId);
            SkyWalkingTraceModels.TraceBrief brief = detail.brief();
            return new ExecutionTraceViews.ExecutionTraceDetailView(true, null,
                    new ExecutionTraceViews.TraceDetailView(
                            traceId, brief.endpointName(), brief.service(),
                            brief.durationMs(), brief.isError(), detail.spans()));
        } catch (RuntimeException exception) {
            boolean snapshotExists = repository.findByExecutionId(executionId).isPresent();
            return new ExecutionTraceViews.ExecutionTraceDetailView(
                    false, snapshotExists ? "retention-expired" : "source-unavailable", null);
        }
    }

    private PersistentScenarioExecutionRecord requireExecution(long executionId) {
        return loadExecution(executionId)
                .orElseThrow(() -> new ExecutionValidationException("execution does not exist"));
    }

    /** 快照内存过滤（service/endpoint/onlyError/minDurationMs）→ 排序 → 分页切片。 */
    private ExecutionTraceViews.ExecutionTracesPageView pageFromBriefs(
            List<SkyWalkingTraceModels.TraceBrief> briefs, String service, String endpoint,
            Boolean onlyError, Long minDurationMs, String sort, int page, int size) {
        Stream<SkyWalkingTraceModels.TraceBrief> stream = briefs.stream()
                .filter(brief -> service == null || service.isBlank() || service.equals(brief.service()))
                .filter(brief -> endpoint == null || endpoint.isBlank() || endpoint.equals(brief.endpointName()))
                .filter(brief -> !Boolean.TRUE.equals(onlyError) || brief.isError())
                .filter(brief -> minDurationMs == null || brief.durationMs() >= minDurationMs);
        Comparator<SkyWalkingTraceModels.TraceBrief> order = "time".equals(sort)
                ? Comparator.comparingLong(SkyWalkingTraceModels.TraceBrief::startEpochMs).reversed()
                : Comparator.comparingLong(SkyWalkingTraceModels.TraceBrief::durationMs).reversed();
        List<SkyWalkingTraceModels.TraceBrief> filtered = stream.sorted(order).toList();
        int fromIndex = (int) Math.min((page - 1L) * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return new ExecutionTraceViews.ExecutionTracesPageView(
                true, null,
                filtered.subList(fromIndex, toIndex).stream().map(ExecutionTraceViews::listItem).toList(),
                filtered.size(), page, size);
    }

    /** 快照 JSON → TraceBrief；损坏按空结果出（终态不得回落 OAP 伪造数据）。 */
    private List<SkyWalkingTraceModels.TraceBrief> parseSnapshot(String tracesJson) {
        try {
            return objectMapper.readValue(tracesJson, new TypeReference<List<SkyWalkingTraceModels.TraceBrief>>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private ExecutionTraceViews.ExecutionTracesPageView unavailablePage(int page, int size, String missingReason) {
        return new ExecutionTraceViews.ExecutionTracesPageView(
                false, missingReason, List.of(), 0, page, size);
    }

    /** 按 executionId 取执行记录（startTime/endTime/status），capture 与 Task 4 查询共用。 */
    private Optional<PersistentScenarioExecutionRecord> loadExecution(long executionId) {
        return executionRepository.findById(executionId);
    }
}
