package com.yr.perftest.platform.envcheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.plandoc.PlanCommentService;
import com.yr.perftest.platform.task.plandoc.PlanMarkdownSupport;
import com.yr.perftest.platform.task.plandoc.PrecheckSettings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 环境检查编排（spec §4）：开跑校验（缺凭据不开始，E8）→ LOCAL 项核验 → 检查项×机器矩阵
 * （机器间并行、单机内串行；探测异常归 WARNING）→ passed/warned 汇总与 detailJson 持久化 → 可选回填。
 */
@Service
public class EnvironmentCheckRunner {

    private static final Set<String> RESULT_STATES = Set.of("OK", "WARNING", "FIXED", "NA");

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final EnvCheckRegistry registry;
    private final EnvCheckCredentialService credentials;
    private final EnvProbeClient probeClient;
    private final PersistentEnvCheckRunRepository runRepository;
    private final PlanCommentService commentService;
    private final ObjectMapper objectMapper;
    private final EnvTargetParser targetParser = new EnvTargetParser();

    public EnvironmentCheckRunner(PersistentTaskPlanRepository planRepository,
                                  PersistentTaskScenarioRepository scenarioRepository,
                                  EnvCheckRegistry registry,
                                  EnvCheckCredentialService credentials,
                                  EnvProbeClient probeClient,
                                  PersistentEnvCheckRunRepository runRepository,
                                  PlanCommentService commentService,
                                  ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.registry = registry;
        this.credentials = credentials;
        this.probeClient = probeClient;
        this.runRepository = runRepository;
        this.commentService = commentService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次环境检查并返回落库的 run 记录。
     * 开跑校验：勾选项含 REMOTE 且 targets 中存在 resolve 为空的 host → 抛 EnvCheckCredentialMissingException（任何探测前）；
     * 无 REMOTE 勾选或 targets 为空 → 只跑 LOCAL（正常放行）。
     */
    public PersistentEnvCheckRunRecord run(long planId, String actor, boolean writeBack) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：task plan does not exist"));
        List<EnvCheckItem> items = registry.resolve(PrecheckSettings.migrate(settingsOf(plan)).items());
        List<RemoteCheckItem> remoteItems = items.stream()
                .filter(RemoteCheckItem.class::isInstance).map(RemoteCheckItem.class::cast).toList();
        List<TargetHost> targets = targetParser.parse(plan.getBody() == null ? "" : plan.getBody());
        boolean remoteEngaged = !remoteItems.isEmpty() && !targets.isEmpty();
        if (remoteEngaged) {
            requireCredentials(plan, targets);
        }
        PersistentEnvCheckRunRecord run = runRepository.save(new PersistentEnvCheckRunRecord(planId, actor));
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>(runLocal(plan, items));
        if (remoteEngaged) {
            rows.addAll(runRemote(plan, remoteItems, targets));
        }
        run.markFinished(countState(rows, "OK", "FIXED"), countState(rows, "WARNING"),
                serializeDetail(targets, rows));
        PersistentEnvCheckRunRecord saved = runRepository.save(run);
        if (writeBack) {
            writeBack(planId, saved, targets.size());
        }
        return saved;
    }

    /** 检查目标视图（spec 2026-09-16 §4）：credential ∈ "POOL" | "PLAN_OVERRIDE" | "MISSING"。 */
    public record TargetView(String host, String module, String credential, int applicableRemoteItems) {
    }

    /** 检查目标预览响应：targets 清单 + total/ready 计数 + 缺凭据 host 清单。 */
    public record TargetsPreview(List<TargetView> targets, int total, int ready, List<String> missing) {
    }

    /** 检查目标预览（spec 2026-09-16 §4）：文档解析 + 凭据三态 + 勾选远程项适用计数；只读，不产生 run。 */
    public TargetsPreview previewTargets(long planId) {
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：task plan does not exist"));
        List<TargetHost> hosts = targetParser.parse(plan.getBody() == null ? "" : plan.getBody());
        List<EnvCheckItem> checked = registry.resolve(PrecheckSettings.migrate(settingsOf(plan)).items());
        List<RemoteCheckItem> remote = checked.stream()
                .filter(RemoteCheckItem.class::isInstance).map(RemoteCheckItem.class::cast).toList();
        List<TargetView> targets = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (TargetHost host : hosts) {
            String state = credentials.resolveRecord(plan.getProjectId(), plan.getId(), host.host())
                    .map(record -> record.getPlanId() != null ? "PLAN_OVERRIDE" : "POOL")
                    .orElse("MISSING");
            if ("MISSING".equals(state)) {
                missing.add(host.host());
            }
            int applicable = (int) remote.stream()
                    .filter(item -> TargetHost.matches(host, item.appliesTo())).count();
            targets.add(new TargetView(host.host(), host.module(), state, applicable));
        }
        return new TargetsPreview(targets, targets.size(), targets.size() - missing.size(), missing);
    }

    /** 修复回写（供 Task 9）：按 host+itemKey 定位结果行更新 state/detail，重算 passed/warned 并持久化。 */
    public void updateResultState(long runId, String host, String itemKey, String state, String detail) {
        if (!RESULT_STATES.contains(state)) {
            throw new EnvCheckValidationException("ENV_CHECK_INVALID：非法结果状态 " + state);
        }
        PersistentEnvCheckRunRecord run = runRepository.findById(runId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：环境检查运行不存在"));
        boolean replaced = false;
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        for (EnvCheckRunService.ResultRow row : parseRows(run.getDetailJson())) {
            if (!replaced && Objects.equals(row.host(), host) && row.itemKey().equals(itemKey)) {
                replaced = true;
                rows.add(new EnvCheckRunService.ResultRow(row.host(), row.itemKey(), state, detail,
                        row.suggestion(), row.method(), row.risk(), row.fixable()));
            } else {
                rows.add(row);
            }
        }
        if (!replaced) {
            throw new EnvCheckValidationException("ENV_CHECK_INVALID：结果行不存在（" + host + "/" + itemKey + "）");
        }
        run.markFinished(countState(rows, "OK", "FIXED"), countState(rows, "WARNING"),
                serializeDetail(parseTargets(run.getDetailJson()), rows));
        runRepository.save(run);
    }

    /** detailJson → 结果行（RunService.detail / runPrecheck 失败汇总共用）。 */
    public List<EnvCheckRunService.ResultRow> parseRows(String detailJson) {
        JsonNode results = readDetail(detailJson).path("results");
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        for (JsonNode node : results) {
            rows.add(new EnvCheckRunService.ResultRow(
                    textOrNull(node, "host"), textOrNull(node, "itemKey"), node.path("state").asText(""),
                    textOrNull(node, "detail"), textOrNull(node, "suggestion"), textOrNull(node, "method"),
                    textOrNull(node, "risk"), node.path("fixable").asBoolean(false)));
        }
        return rows;
    }

    private List<TargetHost> parseTargets(String detailJson) {
        JsonNode nodes = readDetail(detailJson).path("targets");
        List<TargetHost> targets = new ArrayList<>();
        for (JsonNode node : nodes) {
            targets.add(new TargetHost(node.path("host").asText(""), node.path("module").asText("")));
        }
        return targets;
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

    /** 开跑校验（spec E8）：任一目标机缺凭据 → 抛 EnvCheckCredentialMissingException，不开始任何探测。 */
    private void requireCredentials(PersistentTaskPlanRecord plan, List<TargetHost> targets) {
        List<String> missing = targets.stream().map(TargetHost::host).distinct()
                .filter(host -> credentials.resolve(plan.getProjectId(), plan.getId(), host).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            throw new EnvCheckCredentialMissingException(
                    "ENV_CREDENTIALS_MISSING：以下机器未配置 SSH 凭据：" + String.join("、", missing), missing);
        }
    }

    /** LOCAL 项平台本机核验（body + 场景行），每项产出 OK/WARNING 行（host=null）。 */
    private List<EnvCheckRunService.ResultRow> runLocal(PersistentTaskPlanRecord plan, List<EnvCheckItem> items) {
        List<PersistentTaskScenarioRecord> scenarios =
                scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId());
        LocalCheckContext context = new LocalCheckContext(plan.getBody() == null ? "" : plan.getBody(),
                scenarios.stream()
                        .map(s -> new LocalCheckContext.ScenarioRow(s.getName(), s.getScriptVersionId())).toList());
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        for (EnvCheckItem item : items) {
            if (item instanceof LocalCheckItem local) {
                LocalVerdict verdict = local.check(context);
                rows.add(new EnvCheckRunService.ResultRow(null, item.key(), verdict.ok() ? "OK" : "WARNING",
                        verdict.detail(), null, null, null, false));
            }
        }
        return rows;
    }

    /** REMOTE 矩阵：机器间并行（pool 上限 8，finally shutdown），单机内串行。 */
    private List<EnvCheckRunService.ResultRow> runRemote(PersistentTaskPlanRecord plan,
                                                         List<RemoteCheckItem> items,
                                                         List<TargetHost> targets) {
        Map<String, EnvCheckCredentialService.ResolvedCredential> credentialByHost = new LinkedHashMap<>();
        for (TargetHost target : targets) {
            credentialByHost.putIfAbsent(target.host(),
                    credentials.resolve(plan.getProjectId(), plan.getId(), target.host()).orElseThrow());
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(8, targets.size())));
        try {
            List<Future<List<EnvCheckRunService.ResultRow>>> futures = new ArrayList<>();
            for (TargetHost target : targets) {
                futures.add(pool.submit(() -> probeHost(target, items, credentialByHost.get(target.host()))));
            }
            List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
            for (Future<List<EnvCheckRunService.ResultRow>> future : futures) {
                rows.addAll(future.get());
            }
            return rows;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("环境检查被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("环境检查执行失败", exception.getCause());
        } finally {
            pool.shutdown();
        }
    }

    /** 单机串行：不适用 NA → TARGET 项聚合成一批 probeTarget → PLATFORM 项逐条平台本机拨测。 */
    private List<EnvCheckRunService.ResultRow> probeHost(TargetHost target, List<RemoteCheckItem> items,
                                                         EnvCheckCredentialService.ResolvedCredential credential) {
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        List<RemoteCheckItem> targetItems = new ArrayList<>();
        List<ProbeCommand> targetCommands = new ArrayList<>();
        List<RemoteCheckItem> platformItems = new ArrayList<>();
        for (RemoteCheckItem item : items) {
            if (!TargetHost.matches(target, item.appliesTo())) {
                rows.add(new EnvCheckRunService.ResultRow(target.host(), item.key(), "NA",
                        "文档模块「" + (target.module() == null || target.module().isBlank() ? "-" : target.module())
                                + "」不适用",
                        null, null, item.risk().name(), item.fixable()));
                continue;
            }
            if (item.probe(target).location() == ProbeLocation.PLATFORM) {
                platformItems.add(item);
            } else {
                targetItems.add(item);
                targetCommands.add(new ProbeCommand(item.key(), item.probe(target).script()));
            }
        }
        rows.addAll(probeTargetBatch(target, targetItems, targetCommands, credential));
        rows.addAll(probePlatformEach(target, platformItems));
        return rows;
    }

    /** TARGET 通道：一次 env-probe 连接批量执行；批量失败 → 该批全部 WARNING（detail 附原因）。 */
    private List<EnvCheckRunService.ResultRow> probeTargetBatch(TargetHost target, List<RemoteCheckItem> items,
                                                                List<ProbeCommand> commands,
                                                                EnvCheckCredentialService.ResolvedCredential credential) {
        Map<String, ProbeOutcome> outcomes = new LinkedHashMap<>();
        String failure = null;
        if (!commands.isEmpty()) {
            try {
                for (ProbeOutcome outcome : probeClient.probeTarget(credential, commands)) {
                    outcomes.put(outcome.id(), outcome);
                }
            } catch (Exception exception) {
                failure = messageOf(exception);
            }
        }
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        for (RemoteCheckItem item : items) {
            rows.add(evaluate(target, item, outcomes.get(item.key()), failure));
        }
        return rows;
    }

    /** PLATFORM 通道逐条本机执行（不进 SSH 批量通道）；失败 → WARNING。 */
    private List<EnvCheckRunService.ResultRow> probePlatformEach(TargetHost target, List<RemoteCheckItem> items) {
        List<EnvCheckRunService.ResultRow> rows = new ArrayList<>();
        for (RemoteCheckItem item : items) {
            ProbeOutcome outcome;
            try {
                outcome = probeClient.probePlatform(target.host(), 22, item.probe(target).script());
            } catch (Exception exception) {
                rows.add(new EnvCheckRunService.ResultRow(target.host(), item.key(), "WARNING",
                        "探测失败：" + messageOf(exception), null, null, item.risk().name(), item.fixable()));
                continue;
            }
            rows.add(evaluate(target, item, outcome, null));
        }
        return rows;
    }

    /** 单项判定：探测失败/无输出 → WARNING（detail 附原因）；judge 异常同样降级 WARNING。 */
    private EnvCheckRunService.ResultRow evaluate(TargetHost target, RemoteCheckItem item,
                                                  ProbeOutcome outcome, String batchFailure) {
        if (outcome == null) {
            return new EnvCheckRunService.ResultRow(target.host(), item.key(), "WARNING",
                    batchFailure != null ? "探测失败：" + batchFailure : "探测无输出",
                    null, null, item.risk().name(), item.fixable());
        }
        try {
            ProbeVerdict verdict = item.judge(new ProbeOutput(target.host(), outcome.code(), outcome.output()));
            return new EnvCheckRunService.ResultRow(target.host(), item.key(), verdict.ok() ? "OK" : "WARNING",
                    verdict.detail(), verdict.ok() ? null : verdict.suggestion(),
                    verdict.ok() ? null : verdict.method(), item.risk().name(), item.fixable());
        } catch (Exception exception) {
            return new EnvCheckRunService.ResultRow(target.host(), item.key(), "WARNING",
                    "判定异常：" + messageOf(exception), null, null, item.risk().name(), item.fixable());
        }
    }

    /** 回填：系统批注 + 「五、测试资源」章节末摘要行（<!--envcheck:{runId}--> 幂等锚点）。 */
    private void writeBack(long planId, PersistentEnvCheckRunRecord run, int machineCount) {
        String summary = String.format(Locale.ROOT, "%d 台机器 · %d 项通过 · %d 项待处理",
                machineCount, run.getPassed(), run.getWarned());
        commentService.systemComment(planId, run.getTriggeredBy() + " 触发环境检查：" + summary);
        String line = "- " + DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.now()) + " 环境检查：" + summary + "（详见环境检查面板）<!--envcheck:" + run.getId() + "-->";
        PersistentTaskPlanRecord plan = planRepository.findById(planId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CHECK_INVALID：task plan does not exist"));
        plan.updateBody(PlanMarkdownSupport.appendEnvCheckRecord(plan.getBody(), line));
        planRepository.save(plan);
    }

    /** precheckJson 读取 + 惰性迁移（与 PlanWorkflowService.getPrecheckSettings 同口径；不反向依赖 workflow）。 */
    private PrecheckSettings settingsOf(PersistentTaskPlanRecord plan) {
        String json = plan.getPrecheckJson();
        if (json == null || json.isBlank()) {
            return PrecheckSettings.disabled();
        }
        try {
            return PrecheckSettings.migrate(objectMapper.readValue(json, PrecheckSettings.class));
        } catch (Exception exception) {
            return PrecheckSettings.disabled();
        }
    }

    private String serializeDetail(List<TargetHost> targets, List<EnvCheckRunService.ResultRow> rows) {
        try {
            return objectMapper.writeValueAsString(Map.of("targets", targets, "results", rows));
        } catch (Exception exception) {
            return "{\"targets\":[],\"results\":[]}";
        }
    }

    private int countState(List<EnvCheckRunService.ResultRow> rows, String... states) {
        Set<String> wanted = Set.of(states);
        return (int) rows.stream().filter(row -> wanted.contains(row.state())).count();
    }

    private String textOrNull(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
