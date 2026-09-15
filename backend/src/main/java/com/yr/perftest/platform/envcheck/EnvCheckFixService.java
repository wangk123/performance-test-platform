package com.yr.perftest.platform.envcheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.plandoc.PlanCommentService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 修复链路（spec §4.5）：批量修复 = 逐项「重探 → fix() 声明 → 备份 → 修复 → 复查 → diff/FIXED」；
 * 复查不通过保持 WARNING 且不写 fix 记录；回滚按 backupRef 恢复原值并留系统批注；总闸 fix-enabled=false 全拒绝。
 */
@Service
public class EnvCheckFixService {

    /** 修复请求：结果矩阵定位（LOCAL 项 host 为 null，但 LOCAL 项直接跳过）。 */
    public record FixRequest(String host, String itemKey) {
    }

    /** 批量修复结果，元素格式 itemKey@host。 */
    public record FixOutcome(List<String> fixed, List<String> skipped, List<String> failed) {
    }

    private final EnvCheckRegistry registry;
    private final EnvProbeClient probeClient;
    private final EnvCheckCredentialService credentials;
    private final EnvCheckProperties properties;
    private final PersistentEnvCheckRunRepository runRepository;
    private final PersistentEnvCheckFixRepository fixRepository;
    private final PersistentTaskPlanRepository planRepository;
    private final EnvironmentCheckRunner runner;
    private final PlanCommentService commentService;
    private final ObjectMapper objectMapper;

    public EnvCheckFixService(EnvCheckRegistry registry, EnvProbeClient probeClient,
                              EnvCheckCredentialService credentials, EnvCheckProperties properties,
                              PersistentEnvCheckRunRepository runRepository, PersistentEnvCheckFixRepository fixRepository,
                              PersistentTaskPlanRepository planRepository, EnvironmentCheckRunner runner,
                              PlanCommentService commentService, ObjectMapper objectMapper) {
        this.registry = registry;
        this.probeClient = probeClient;
        this.credentials = credentials;
        this.properties = properties;
        this.runRepository = runRepository;
        this.fixRepository = fixRepository;
        this.planRepository = planRepository;
        this.runner = runner;
        this.commentService = commentService;
        this.objectMapper = objectMapper;
    }

    /** 批量修复：单项失败不中断批次；全部被总闸拒绝时抛 IllegalStateException。 */
    public FixOutcome apply(long runId, List<FixRequest> requests, String actor) {
        if (!properties.isFixEnabled()) {
            throw new IllegalStateException("环境检查修复已被平台关闭");
        }
        PersistentEnvCheckRunRecord run = requireRun(runId);
        PersistentTaskPlanRecord plan = requirePlan(run.getPlanId());
        List<EnvCheckRunService.ResultRow> rows = runner.parseRows(run.getDetailJson());
        List<String> fixed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (FixRequest request : requests == null ? List.<FixRequest>of() : requests) {
            String label = label(request);
            if (!fixableAndWarned(request, rows)) {
                skipped.add(label);
                continue;
            }
            try {
                switch (applyOne(runId, plan, request, actor)) {
                    case FIXED -> fixed.add(label);
                    case SKIP_NO_SPEC -> skipped.add(label);
                    case FAILED -> failed.add(label);
                }
            } catch (Exception exception) {
                failed.add(label);
            }
        }
        if (!fixed.isEmpty()) {
            commentService.systemComment(plan.getId(),
                    actor + " 修复 " + fixed.size() + " 项：" + String.join("、", fixed));
        }
        return new FixOutcome(List.copyOf(fixed), List.copyOf(skipped), List.copyOf(failed));
    }

    /** 单项修复（spec §4.5 ②→⑧）的执行段：③ fix() 为空归 SKIP_NO_SPEC；备份/修复/复查任一失败归 FAILED（保持 WARNING）。 */
    private ItemResult applyOne(long runId, PersistentTaskPlanRecord plan, FixRequest request, String actor) throws Exception {
        RemoteCheckItem item = (RemoteCheckItem) registry.byKey(request.itemKey()).orElseThrow();
        TargetHost target = targetOf(runId, request.host());
        EnvCheckCredentialService.ResolvedCredential credential =
                credentials.resolve(plan.getProjectId(), plan.getId(), request.host())
                        .orElseThrow(() -> new IllegalStateException("凭据缺失：" + request.host()));
        ProbeOutput probed = probe(item, target, credential);
        if (probed == null) {
            return ItemResult.FAILED;
        }
        FixSpec spec = item.fix(target, probed).orElse(null);
        if (spec == null) {
            return ItemResult.SKIP_NO_SPEC;
        }
        ProbeOutcome backup = execute(item, target, credential, spec.backupScript());
        if (backup == null || backup.code() != 0 || firstLine(backup.output()).isBlank()) {
            return ItemResult.FAILED;
        }
        String backupRef = firstLine(backup.output());
        ProbeOutcome applyOutcome = execute(item, target, credential, spec.applyScript());
        if (applyOutcome == null || applyOutcome.code() != 0) {
            return ItemResult.FAILED;
        }
        ProbeOutput after = probe(item, target, credential);
        if (after == null || !item.judge(after).ok()) {
            return ItemResult.FAILED;
        }
        fixRepository.save(new PersistentEnvCheckFixRecord(runId, request.host(), request.itemKey(),
                spec.risk().name(), backupRef, spec.summary(), spec.summary(), actor));
        runner.updateResultState(runId, request.host(), request.itemKey(), "FIXED", spec.summary());
        return ItemResult.FIXED;
    }

    /** 回滚：校验未回滚过 → rollbackScript（{backupRef} 替换）执行 → markRolledBack → 结果行退回 WARNING → 系统批注。 */
    public void rollback(long fixId, String actor) {
        PersistentEnvCheckFixRecord fix = requireFix(fixId);
        if (fix.getRolledBackAt() != null) {
            throw new IllegalStateException("该修复已回滚，不能重复回滚");
        }
        PersistentEnvCheckRunRecord run = requireRun(fix.getRunId());
        PersistentTaskPlanRecord plan = requirePlan(run.getPlanId());
        RemoteCheckItem item = (RemoteCheckItem) registry.byKey(fix.getItemKey())
                .orElseThrow(() -> new IllegalStateException("检查项不存在：" + fix.getItemKey()));
        TargetHost target = targetOf(fix.getRunId(), fix.getHost());
        EnvCheckCredentialService.ResolvedCredential credential =
                credentials.resolve(plan.getProjectId(), plan.getId(), fix.getHost())
                        .orElseThrow(() -> new IllegalStateException("凭据缺失：" + fix.getHost()));
        FixSpec spec = item.fix(target, degradedOutput(fix.getHost())).orElse(null);        if (spec == null || spec.rollbackScript() == null || spec.rollbackScript().isBlank()) {
            throw new IllegalStateException("检查项无回滚脚本");
        }
        ProbeOutcome rollbackOutcome =
                execute(item, target, credential, spec.rollbackScript().replace("{backupRef}", fix.getBackupRef()));
        if (rollbackOutcome == null || rollbackOutcome.code() != 0) {
            throw new IllegalStateException("回滚脚本执行失败");
        }
        fix.markRolledBack(Instant.now());
        fixRepository.save(fix);
        runner.updateResultState(fix.getRunId(), fix.getHost(), fix.getItemKey(), "WARNING", "已回滚");
        commentService.systemComment(plan.getId(), actor + " 回滚 " + fix.getItemKey() + "@" + fix.getHost());
    }

    /** 供 Controller 鉴权：按 id 取修复记录，不存在抛 400 语义异常。 */
    public PersistentEnvCheckFixRecord requireFix(long fixId) {
        return fixRepository.findById(fixId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：修复记录不存在"));
    }

    /** ① registry/可修/状态门槛：非 REMOTE、不可修、状态非 WARNING 一律跳过。 */
    private boolean fixableAndWarned(FixRequest request, List<EnvCheckRunService.ResultRow> rows) {
        return registry.byKey(request.itemKey()).filter(RemoteCheckItem.class::isInstance)
                .map(RemoteCheckItem.class::cast)
                .filter(RemoteCheckItem::fixable)
                .filter(ignored -> rows.stream().anyMatch(row -> Objects.equals(row.host(), request.host())
                        && row.itemKey().equals(request.itemKey()) && "WARNING".equals(row.state())))
                .isPresent();
    }

    /** 单项执行结论。 */
    private enum ItemResult { FIXED, SKIP_NO_SPEC, FAILED }

    /** 按探测声明通道执行单条脚本：TARGET 走一次 env-probe 连接，PLATFORM 平台本机直跑。 */
    private ProbeOutcome execute(RemoteCheckItem item, TargetHost target,
                                 EnvCheckCredentialService.ResolvedCredential credential, String script) {
        if (item.probe(target).location() == ProbeLocation.PLATFORM) {
            return probeClient.probePlatform(target.host(), 22, script);
        }
        return probeClient.probeTarget(credential, List.of(new ProbeCommand(item.key(), script)))
                .stream().findFirst().orElse(null);
    }

    /** 重探单项：探测异常向上抛（调用方归 failed）；无输出归 null。 */
    private ProbeOutput probe(RemoteCheckItem item, TargetHost target,
                              EnvCheckCredentialService.ResolvedCredential credential) throws Exception {
        ProbeOutcome outcome = execute(item, target, credential, item.probe(target).script());
        return outcome == null ? null : new ProbeOutput(target.host(), outcome.code(), outcome.output());
    }

    /** 回滚重建 FixSpec 用的占位输出：脚本为常量声明，不依赖真实探测值，避免回滚前多余网络往返。 */
    private ProbeOutput degradedOutput(String host) {
        return new ProbeOutput(host, -1, "");
    }

    private TargetHost targetOf(long runId, String host) {
        JsonNode nodes = readDetail(requireRun(runId).getDetailJson()).path("targets");
        for (JsonNode node : nodes) {
            if (host.equals(node.path("host").asText(""))) {
                return new TargetHost(host, node.path("module").asText(""));
            }
        }
        throw new IllegalStateException("目标机不在本次检查范围：" + host);
    }

    private JsonNode readDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(detailJson);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String label(FixRequest request) {
        return request.itemKey() + "@" + request.host();
    }

    private String firstLine(String output) {
        return output == null ? "" : output.lines().findFirst().orElse("").trim();
    }

    private PersistentEnvCheckRunRecord requireRun(long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：环境检查运行不存在"));
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：task plan does not exist"));
    }
}
