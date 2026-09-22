package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceKind;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceProperties;
import com.yr.perftest.platform.evidence.deep.SkyWalkingGraphqlClient;
import com.yr.perftest.platform.evidence.deep.SkyWalkingTraceModels;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 执行 trace 查询服务。Task 6 只含终态快照职责（capture/load/delete）；
 * 执行详情链路面板的在线查询方法由 Task 4 在本类扩展，
 * 复用 {@link #loadExecution} 取执行时间窗与状态。
 */
@Service
public class ExecutionTraceQueryService {
    /** 终态快照按耗时取 Top500（与链路面板快照回看约定一致）。 */
    static final int SNAPSHOT_LIMIT = 500;

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
                    null, null, execution.getStartTime(), to, null, null, true, 1, SNAPSHOT_LIMIT);
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

    /** 按 executionId 取执行记录（startTime/endTime/status），capture 与 Task 4 查询共用。 */
    private Optional<PersistentScenarioExecutionRecord> loadExecution(long executionId) {
        return executionRepository.findById(executionId);
    }
}
