package com.yr.perftest.platform.envcheck;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** 环境检查运行域服务：触发（建 run 记录 → 编排执行 → 持久化 + 回填）、历史、详情（detailJson 反序列化）。 */
@Service
public class EnvCheckRunService {

    private final EnvironmentCheckRunner runner;
    private final PersistentEnvCheckRunRepository runRepository;

    public EnvCheckRunService(EnvironmentCheckRunner runner, PersistentEnvCheckRunRepository runRepository) {
        this.runner = runner;
        this.runRepository = runRepository;
    }

    /** 结果矩阵单行：state ∈ OK|WARNING|FIXED|NA；LOCAL 行 host=null，risk=null。 */
    public record ResultRow(String host, String itemKey, String state, String detail,
                            String suggestion, String method, String risk, boolean fixable) {
    }

    /** 运行详情：run 记录 + 结果矩阵行。 */
    public record RunDetail(PersistentEnvCheckRunRecord run, List<ResultRow> rows) {
    }

    /** 历史摘要视图（不含 detailJson）。 */
    public record RunSummary(long id, long planId, String triggeredBy, Instant startedAt, Instant finishedAt,
                             int passed, int warned) {
    }

    /** 触发一次环境检查（写回批注 + 测试资源摘要行）；目标机缺凭据抛 EnvCheckCredentialMissingException（400）。 */
    public PersistentEnvCheckRunRecord trigger(long planId, String actor) {
        return runner.run(planId, actor, true);
    }

    public List<PersistentEnvCheckRunRecord> history(long planId) {
        return runRepository.findTop50ByPlanIdOrderByStartedAtDesc(planId);
    }

    public RunDetail detail(long runId) {
        PersistentEnvCheckRunRecord run = requireRun(runId);
        return new RunDetail(run, runner.parseRows(run.getDetailJson()));
    }

    public RunSummary toSummary(PersistentEnvCheckRunRecord run) {
        return new RunSummary(run.getId(), run.getPlanId(), run.getTriggeredBy(), run.getStartedAt(),
                run.getFinishedAt(), run.getPassed(), run.getWarned());
    }

    private PersistentEnvCheckRunRecord requireRun(long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：环境检查运行不存在"));
    }
}
