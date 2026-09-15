package com.yr.perftest.platform.task.plandoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.ExecutionQueryService;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 计划状态机流转（设计 §4/§6）；批注域见 PlanCommentService。报告/发布/分享/预检分任务追加；模板 CRUD 自本任务起。 */
@Service
public class PlanWorkflowService {

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PlanCommentService commentService;
    private final ProjectAccessResolver accessResolver;
    private final PersistentPlanTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final PlanDocumentService documentService;
    private final ExecutionQueryService executionQueryService;
    private final PersistentPlanShareTokenRepository shareTokenRepository;
    private final PersistentPlanPublishSnapshotRepository snapshotRepository;
    private final TaskPlanService planService;
    private final ScenarioThreadGroupConfigSupport configSupport;
    private final PlanVerdictService verdictService;
    private final PlanVersionService versionService;

    public PlanWorkflowService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PlanCommentService commentService,
            ProjectAccessResolver accessResolver,
            PersistentPlanTemplateRepository templateRepository,
            ObjectMapper objectMapper,
            PlanDocumentService documentService,
            ExecutionQueryService executionQueryService,
            PersistentPlanShareTokenRepository shareTokenRepository,
            PersistentPlanPublishSnapshotRepository snapshotRepository,
            TaskPlanService planService,
            ScenarioThreadGroupConfigSupport configSupport,
            PlanVerdictService verdictService,
            PlanVersionService versionService
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.commentService = commentService;
        this.accessResolver = accessResolver;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
        this.documentService = documentService;
        this.executionQueryService = executionQueryService;
        this.shareTokenRepository = shareTokenRepository;
        this.snapshotRepository = snapshotRepository;
        this.planService = planService;
        this.configSupport = configSupport;
        this.verdictService = verdictService;
        this.versionService = versionService;
    }

    @Transactional
    public void submit(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SUBMIT");
        requireStatus(plan, PlanStatus.PLANNING, "SUBMIT");
        plan.transitionTo(PlanStatus.IN_REVIEW);
        commentService.systemComment(planId, actor.username() + " 提交评审");
        if (comment != null && !comment.isBlank()) {
            commentService.appendReviewNote(planId, actor.username(), comment.trim());
        }
    }

    @Transactional
    public void approve(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "APPROVE");
        requireStatus(plan, PlanStatus.IN_REVIEW, "APPROVE");
        plan.transitionTo(PlanStatus.EXECUTING);
        commentService.systemComment(planId, "评审通过，进入执行阶段（审批人：" + actor.username() + "）");
        if (comment != null && !comment.isBlank()) {
            commentService.appendReviewNote(planId, actor.username(), comment.trim());
        }
    }

    /** 执行完成：人工确认进入报告阶段（spec §4.1 迁移 3）。活跃执行只由前端二次确认告警，后端不拦截。 */
    @Transactional
    public void finishExecution(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "FINISH");
        requireStatus(plan, PlanStatus.EXECUTING, "FINISH");
        plan.transitionTo(PlanStatus.REPORTING);
        commentService.systemComment(planId, actor.username() + " 执行完成，进入报告阶段");
    }

    public record PrecheckReport(boolean ok, List<String> failures, List<String> autoPassed) {
    }

    /** 执行门禁（设计 §10.1）：状态 + 脚本 + 首执行环境检查。返回 planId。 */
    @Transactional
    public long assertExecutionAllowed(long scenarioId) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：scenario does not exist"));
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        boolean phaseOk = plan.getStatus() == PlanStatus.EXECUTING || plan.getStatus() == PlanStatus.REPORTING;
        if (!phaseOk) {
            throw new PlanStateException("PLAN_STATE：请先通过评审进入执行阶段（当前 " + plan.getStatus() + "）",
                    plan.getStatus(), List.of("SUBMIT", "APPROVE"));
        }
        if (scenario.getScriptVersionId() == null) {
            throw new PlanValidationException("PLAN_INVALID：场景「" + scenario.getName() + "」未关联脚本，无法执行");
        }
        PrecheckSettings settings = getPrecheckSettings(plan.getId());
        if (settings.enabled() && plan.getPrecheckExecutedAt() == null) {
            PrecheckReport report = runPrecheck(plan.getId(), true);
            if (!report.ok()) {
                throw new PlanPrecheckFailedException("PLAN_PRECHECK_FAILED：环境检查未通过——"
                        + String.join("；", report.failures()), report.failures());
            }
            plan.markPrecheckExecuted(Instant.now());
        }
        return plan.getId();
    }

    /** 终态联动：回填执行摘要（设计 §4.5 执行生命周期不再回写计划状态）。 */
    @Transactional
    public void onExecutionTerminal(long executionId) {
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null) {
            return;
        }
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(execution.getScenarioId()).orElse(null);
        if (scenario == null) {
            return;
        }
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        String entryLine = buildEntryLine(execution);
        documentService.backfillExecutionRecord(plan.getId(), scenario.getName(), executionId, entryLine);
    }

    private String buildEntryLine(PersistentScenarioExecutionRecord execution) {
        int threads = 0;
        try {
            com.fasterxml.jackson.databind.JsonNode config = objectMapper.readTree(execution.getConfigJson());
            threads = config.path("threads").asInt(0);
        } catch (Exception ignored) {
        }
        long p95 = 0;
        double throughput = 0d;
        double errorRate = 0d;
        try {
            com.yr.perftest.platform.execution.TaskExecutionResult.Summary summary =
                    executionQueryService.getResult(execution.getId()).summary();
            if (summary != null) {
                p95 = summary.p95();
                throughput = summary.throughput();
                errorRate = summary.errorRate();
            }
        } catch (Exception ignored) {
        }
        String endedAt = execution.getEndTime() == null ? "-"
                : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(execution.getEndTime());
        return String.format(java.util.Locale.ROOT,
                "- %s · %d 并发 · %s · 吞吐 %.1f TPS · P95 %d ms · 错误率 %.2f%%",
                endedAt, threads, execution.getStatus(), throughput, p95, errorRate);
    }

    /** 评估检测清单：自动项核验；人工项视为未确认=失败项（设计 §10.3）。 */
    @Transactional
    public PrecheckReport runPrecheck(long planId, boolean writeBackChecklist) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PrecheckSettings settings = getPrecheckSettings(planId);
        List<String> failures = new java.util.ArrayList<>();
        List<String> autoPassed = new java.util.ArrayList<>();
        String body = plan.getBody() == null ? "" : plan.getBody();
        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId);
        for (String item : settings.items()) {
            String plain = item.replaceAll("（.*?）$", "").trim();
            boolean auto;
            boolean pass;
            switch (plain) {
                case "指标已定义" -> {
                    auto = true;
                    pass = PlanMarkdownSupport.extractSection(body, "三、测试指标") != null
                            && PlanMarkdownSupport.extractSection(body, "三、测试指标").contains("|---");
                }
                case "场景已配置" -> {
                    auto = true;
                    pass = !scenarios.isEmpty();
                }
                case "脚本已关联" -> {
                    auto = true;
                    pass = scenarios.stream().allMatch(s -> s.getScriptVersionId() != null);
                }
                default -> {
                    auto = false;
                    pass = false;
                }
            }
            if (auto && pass) {
                autoPassed.add(plain);
            } else {
                failures.add(auto ? plain + "（自动核验未通过）" : plain + "（待人工确认）");
            }
        }
        if (writeBackChecklist && !autoPassed.isEmpty()) {
            writeBackEntryChecklist(plan, body, autoPassed);
        }
        return new PrecheckReport(failures.isEmpty(), List.copyOf(failures), List.copyOf(autoPassed));
    }

    /** 自动通过项回写入口准则勾选（系统回填：revision+1）。 */
    private void writeBackEntryChecklist(PersistentTaskPlanRecord plan, String body, List<String> autoPassed) {
        String constraints = PlanMarkdownSupport.extractSection(body, "六、测试约束");
        if (constraints == null) {
            return;
        }
        String updated = constraints;
        for (String item : autoPassed) {
            updated = updated.replace("- [ ] " + item + "（自动）", "- [x] " + item + "（自动）");
        }
        if (!updated.equals(constraints)) {
            plan.updateBody(PlanMarkdownSupport.replaceSection(body, "六、测试约束", updated));
        }
    }

    @Transactional
    public void precheckSkip(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        plan.markPrecheckExecuted(Instant.now());
        commentService.systemComment(planId, "跳过环境检查继续执行（操作人：" + (actor == null ? "?" : actor.username()) + "）");
    }

    @Transactional(readOnly = true)
    public PrecheckSettings getPrecheckSettings(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPrecheckJson() == null || plan.getPrecheckJson().isBlank()) {
            return PrecheckSettings.disabled();
        }
        try {
            PrecheckSettings parsed = objectMapper.readValue(plan.getPrecheckJson(), PrecheckSettings.class);
            return parsed.items() == null ? new PrecheckSettings(parsed.enabled(), PrecheckSettings.DEFAULT_ITEMS) : parsed;
        } catch (Exception exception) {
            return PrecheckSettings.disabled();
        }
    }

    @Transactional
    public void updatePrecheckSettings(long planId, HumanPrincipal actor, PrecheckSettings settings) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireActor(planId, actor, "PRECHECK_RUN"); // 成员级动作，沿用权限矩阵
        if (settings == null || settings.items() == null) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置不合法");
        }
        try {
            plan.updatePrecheckJson(objectMapper.writeValueAsString(settings));
        } catch (Exception exception) {
            throw new PlanValidationException("PLAN_INVALID：precheck 设置序列化失败");
        }
    }

    @Transactional(readOnly = true)
    public List<PersistentPlanTemplateRecord> listTemplates(long projectId) {
        return templateRepository.findAllVisible(projectId);
    }

    @Transactional
    public PersistentPlanTemplateRecord createTemplate(long projectId, HumanPrincipal actor, String name, String description, String content) {
        requireTemplateManager(projectId, actor);
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：模板名称与内容不能为空");
        }
        return templateRepository.save(new PersistentPlanTemplateRecord(projectId, name.trim(), description, content, false, actor.username()));
    }

    @Transactional
    public PersistentPlanTemplateRecord updateTemplate(long templateId, HumanPrincipal actor, String name, String description, String content) {
        PersistentPlanTemplateRecord template = templateRepository.findById(templateId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：模板不存在"));
        if (template.isBuiltin()) {
            throw new PlanValidationException("PLAN_INVALID：内置模板不可编辑");
        }
        requireTemplateManager(template.getProjectId(), actor);
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：模板名称与内容不能为空");
        }
        template.update(name.trim(), description, content);
        return template;
    }

    @Transactional
    public void deleteTemplate(long templateId, HumanPrincipal actor) {
        PersistentPlanTemplateRecord template = templateRepository.findById(templateId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：模板不存在"));
        if (template.isBuiltin()) {
            throw new PlanValidationException("PLAN_INVALID：内置模板不可删除");
        }
        requireTemplateManager(template.getProjectId(), actor);
        templateRepository.delete(template);
    }

    /** 模板管理仅项目 OWNER/系统 ADMIN（设计 §7.1）；内置模板（projectId=null）不可在此管理。 */
    private void requireTemplateManager(Long projectId, HumanPrincipal actor) {
        if (projectId == null || actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅项目 OWNER/系统管理员可管理模板");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(projectId, actor, null);
        if (role != ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                && role != ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅项目 OWNER/系统管理员可管理模板");
        }
    }

    public record ShareView(long id, long planId, String token, Instant expiresAt, Instant revokedAt, String createdBy, Instant createdAt) {
    }

    public record SharedPlanView(String name, String body, Instant publishedAt) {
    }

    @Transactional
    public ShareView createShare(long planId, HumanPrincipal actor, Integer expiresInDays) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SHARE");
        int days = expiresInDays == null || expiresInDays <= 0 ? 30 : expiresInDays;
        PersistentPlanShareTokenRecord saved = shareTokenRepository.save(new PersistentPlanShareTokenRecord(
                planId, java.util.UUID.randomUUID().toString(),
                Instant.now().plus(java.time.Duration.ofDays(days)), actor.username()));
        return toShareView(saved);
    }

    @Transactional(readOnly = true)
    public List<ShareView> listShares(long planId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE");
        return shareTokenRepository.findAllByPlanIdOrderByIdDesc(planId).stream().map(this::toShareView).toList();
    }

    @Transactional
    public void revokeShare(long planId, long tokenId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE");
        PersistentPlanShareTokenRecord token = shareTokenRepository.findById(tokenId)
                .filter(t -> t.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在"));
        token.revoke();
    }

    @Transactional(readOnly = true)
    public SharedPlanView getSharedPlan(String token) {
        PersistentPlanShareTokenRecord record = shareTokenRepository.findByToken(token == null ? "" : token)
                .orElseThrow(() -> new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在"));
        if (record.getRevokedAt() != null
                || (record.getExpiresAt() != null && record.getExpiresAt().isBefore(Instant.now()))) {
            throw new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在");
        }
        PersistentTaskPlanRecord plan = requirePlan(record.getPlanId());
        if (plan.getStatus() != PlanStatus.PUBLISHED) {
            throw new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在");
        }
        return new SharedPlanView(plan.getName(), plan.getBody(), plan.getPublishedAt());
    }

    private ShareView toShareView(PersistentPlanShareTokenRecord record) {
        return new ShareView(record.getId(), record.getPlanId(), record.getToken(),
                record.getExpiresAt(), record.getRevokedAt(), record.getCreatedBy(), record.getCreatedAt());
    }

    public record SnapshotView(long id, int revision, String publishedBy, Instant publishedAt) {
    }

    @Transactional
    public TaskPlan publish(long planId, HumanPrincipal actor, String conclusion, String versionNo) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "PUBLISH");
        requireStatus(plan, PlanStatus.REPORTING, "PUBLISH");
        if (conclusion == null || conclusion.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：发布必须填写总体结论");
        }
        String body = plan.getBody() == null ? "" : plan.getBody();
        // 发布即生成（报告 Tab 手动动作并入）：冻结前回填执行结果总览，
        // 有验收指标重绘判等表，无指标维持 P0-1 结论实际列填充
        PlanAcceptanceParser.AcceptanceSection acceptance = PlanAcceptanceParser.parseLeniently(body);
        body = upsertReportOverview(body, buildScenarioOverviews(planId));
        if (acceptance.present()) {
            body = upsertVerdictTable(body, verdictService.compute(planId));
        } else {
            body = fillConclusionActualColumn(body, latestScenarioSummaries(planId));
        }
        String line = "**总体结论**：" + conclusion.trim();
        if (body.contains("**总体结论**：")) {
            int start = body.indexOf("**总体结论**：");
            int end = body.indexOf('\n', start);
            body = end < 0 ? body.substring(0, start) + line : body.substring(0, start) + line + body.substring(end);
        } else {
            String conclusionSection = PlanMarkdownSupport.extractSection(body, "十二、结论");
            body = PlanMarkdownSupport.ensureSection(body, "十二、结论",
                    (conclusionSection == null ? "" : conclusionSection) + "\n" + line + "\n");
        }
        plan.updateBody(body);
        Instant now = Instant.now();
        buildPublishSnapshot(plan, actor.username(), now);
        plan.applyPublish(now);
        // 修订记录合并（spec §9）：发布动作登记为 kind=PUBLISH 版本，快照=含结论正文、plan_phase 历史快照列写入当前状态
        versionService.publishForWorkflow(planId, actor, versionNo, conclusion);
        commentService.systemComment(planId, "已发布（revision=" + plan.getRevision() + "，发布人：" + actor.username() + "）");
        return planService.getPlan(planId);
    }

    @Transactional(readOnly = true)
    public List<SnapshotView> listSnapshots(long planId, HumanPrincipal actor) {
        requireActor(planId, actor, "SHARE"); // 成员级门槛（非成员 403）+ 状态门槛：SHARE 仅 PUBLISHED 开放
        return snapshotRepository.findAllByPlanIdOrderByRevisionDesc(planId).stream()
                .map(s -> new SnapshotView(s.getId(), s.getRevision(), s.getPublishedBy(), s.getPublishedAt()))
                .toList();
    }

    @Transactional
    public PersistentPlanPublishSnapshotRecord buildPublishSnapshot(PersistentTaskPlanRecord plan, String publishedBy, Instant at) {
        List<PersistentTaskScenarioRecord> scenarios = scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(plan.getId());
        String scenarioJson = writeJsonOrEmpty(scenarios.stream().map(s -> java.util.Map.of(
                "id", s.getId(),
                "name", s.getName(),
                "testType", s.getTestType() == null ? "" : s.getTestType().name(),
                "purpose", s.getPurpose() == null ? "" : s.getPurpose(),
                "scriptVersionId", s.getScriptVersionId() == null ? 0L : s.getScriptVersionId(),
                "threadGroupConfigs", configSupport.readStored(s.getThreadGroupConfigsJson())
        )).toList());
        String summaryJson = writeJsonOrEmpty(latestScenarioSummaries(plan.getId()));
        return snapshotRepository.save(new PersistentPlanPublishSnapshotRecord(
                plan.getId(), plan.getRevision(), publishedBy, at,
                writeJsonOrEmpty(java.util.Map.of("body", plan.getBody() == null ? "" : plan.getBody())),
                scenarioJson, summaryJson));
    }

    /** 每场景最近一次终态执行的摘要行（发布快照 summaryJson 与达成表填充共用）。 */
    private List<java.util.Map<String, String>> latestScenarioSummaries(long planId) {
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        for (PersistentTaskScenarioRecord scenario : scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId)) {
            executionRepository.findFirstByScenarioIdOrderByIdDesc(scenario.getId()).ifPresent(execution -> {
                String line = buildEntryLine(execution);
                result.add(java.util.Map.of(
                        "scenarioName", scenario.getName(),
                        "status", execution.getStatus().name(),
                        "summary", line.substring(2)));
            });
        }
        return result;
    }

    private String buildScenarioOverviews(long planId) {
        StringBuilder block = new StringBuilder();
        for (java.util.Map<String, String> summary : latestScenarioSummaries(planId)) {
            block.append("- ").append(summary.get("scenarioName")).append(" · ")
                    .append(summary.get("status")).append(" · ").append(summary.get("summary")).append('\n');
        }
        if (block.isEmpty()) {
            block.append("- （暂无执行记录）\n");
        }
        return block.toString();
    }

    /**
     * 幂等替换报告总览块（设计 §8 不变量）：标记 `<!-- backfill:report -->` 之前的内容不动；
     * 从标记行整块替换到块尾——块自身的 `#### 执行结果总览` 标题行与其后条目行均属块内，
     * 块尾 = 其后第一个其他标题行（`## `/`### `/`#### `）或 `**总体结论**` 行之前；其后内容不动。
     */
    private String upsertReportOverview(String body, String overviewLines) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
        String block = "<!-- backfill:report -->\n#### 执行结果总览（生成于 " + timestamp + "）\n" + overviewLines;
        int marker = body.indexOf("<!-- backfill:report -->");
        if (marker < 0) {
            String conclusion = PlanMarkdownSupport.extractSection(body, "十二、结论");
            if (conclusion == null) {
                return PlanMarkdownSupport.ensureSection(body, "十二、结论", "\n" + block);
            }
            return PlanMarkdownSupport.replaceSection(body, "十二、结论", conclusion + block);
        }
        return body.substring(0, marker) + block + body.substring(blockEndOf(body, marker));
    }

    /**
     * 幂等重绘达成表（spec §5/§6）：标记 `<!-- backfill:verdict -->` 之前的内容不动；
     * 无标记时替换「### 指标达成表」占位小节（标题行到下一标题行），两处皆无则追加到结论章节尾。
     * 块尾 = 其后第一个其它标题行或 `**总体结论**` 行之前（块自身 #### 指标达成表 标题除外）。
     */
    private String upsertVerdictTable(String body, PlanVerdictService.VerdictResult verdict) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(Instant.now());
        StringBuilder table = new StringBuilder("| 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |\n|---|---|---|---|---|---|\n");
        for (PlanVerdictService.VerdictRow row : verdict.rows()) {
            table.append("| ").append(row.objectName()).append(" | ").append(row.metricRaw())
                    .append(" | ").append(row.targetRaw()).append(" | ").append(row.actualValue())
                    .append(" | ").append(verdictStatusText(row.status())).append(" | ")
                    .append(row.reason()).append(" |\n");
        }
        String block = "<!-- backfill:verdict -->\n#### 指标达成表（生成于 " + timestamp + "）\n\n"
                + table + "\n- " + verdict.prefillConclusion() + "\n";
        int marker = body.indexOf("<!-- backfill:verdict -->");
        if (marker >= 0) {
            return body.substring(0, marker) + block + body.substring(blockEndOf(body, marker));
        }
        int subsection = indexOfLine(body, "### 指标达成表");
        if (subsection >= 0) {
            int end = blockEndOf(body, subsection);
            return body.substring(0, subsection) + block + body.substring(end);
        }
        String conclusion = PlanMarkdownSupport.extractSection(body, "十二、结论");
        if (conclusion == null) {
            return PlanMarkdownSupport.ensureSection(body, "十二、结论", "\n" + block);
        }
        return PlanMarkdownSupport.replaceSection(body, "十二、结论", conclusion + block);
    }

    /** 从 startLine 起找块尾：其后第一个其它标题行（跳过本块 `#### 指标达成表`/`#### 执行结果总览`）或 `**总体结论**` 行前。 */
    private int blockEndOf(String body, int startOffset) {
        int end = body.length();
        int offset = startOffset;
        for (String line : body.substring(startOffset).split("\n", -1)) {
            if (offset > startOffset && (line.startsWith("#") || line.startsWith("**总体结论**"))
                    && !line.startsWith("#### 指标达成表") && !line.startsWith("#### 执行结果总览")) {
                end = offset;
                break;
            }
            offset += line.length() + 1;
        }
        return Math.min(end, body.length());
    }

    private int indexOfLine(String body, String exactLine) {
        int offset = 0;
        for (String line : body.split("\n", -1)) {
            if (line.trim().equals(exactLine)) {
                return offset;
            }
            offset += line.length() + 1;
        }
        return -1;
    }

    private String verdictStatusText(PlanVerdictService.VerdictStatus status) {
        return switch (status) {
            case ACHIEVED -> "达成";
            case MISSED -> "未达成";
            case INDETERMINATE -> "无法判定";
        };
    }

    /** 达成表"实际"列自适应（spec §3.5 模板改列后兼容旧表）：表头含「实际」或「实际结果」的列下标，缺省 2。 */
    private String fillConclusionActualColumn(String body, List<java.util.Map<String, String>> summaries) {
        String conclusion = PlanMarkdownSupport.extractSection(body, "十二、结论");
        if (conclusion == null) {
            return body;
        }
        StringBuilder updated = new StringBuilder();
        java.util.Set<String> knownScenarios = new java.util.HashSet<>();
        for (java.util.Map<String, String> summary : summaries) {
            knownScenarios.add(summary.get("scenarioName"));
        }
        int actualColumn = 2; // 旧占位表「实际结果」列
        for (String line : conclusion.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && !trimmed.startsWith("|---")) {
                String[] cells = trimmed.substring(1, trimmed.lastIndexOf('|')).split("\\|", -1);
                int actualIdx = -1;
                for (int i = 0; i < cells.length; i++) {
                    String c = cells[i].trim();
                    if (c.equals("实际结果") || c.equals("实际")) {
                        actualIdx = i;
                        break;
                    }
                }
                if (actualIdx >= 0) { // 表头行：锁定实际列下标，原样输出
                    actualColumn = actualIdx;
                    updated.append(line).append('\n');
                    continue;
                }
                String firstCell = cells.length > 0 ? cells[0].trim() : "";
                String matched = knownScenarios.stream().filter(firstCell::contains).findFirst().orElse(null);
                if (matched != null) {
                    String summary = summaries.stream()
                            .filter(s -> s.get("scenarioName").equals(matched)).findFirst().orElseThrow().get("summary");
                    line = replaceTableRowCell(line, actualColumn, summary);
                }
            }
            updated.append(line).append('\n');
        }
        return PlanMarkdownSupport.replaceSection(body, "十二、结论", updated.toString());
    }

    /** 替换 Markdown 表格行第 index 个数据单元格（0 基）。 */
    private String replaceTableRowCell(String row, int cellIndex, String newValue) {
        String[] cells = row.split("\\|", -1);
        // cells[0] 为行首 | 前的空串；数据单元格从 cells[1] 开始
        int target = cellIndex + 1;
        if (target >= cells.length - 1) {
            return row;
        }
        cells[target] = " " + newValue + " ";
        return String.join("|", cells);
    }

    private String writeJsonOrEmpty(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    /** 校验动作权限并返回计划记录；非项目成员 403；成员但状态不允许 409（附允许动作）。 */
    public PersistentTaskPlanRecord requireActor(long planId, HumanPrincipal actor, String action) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
        Map<String, Boolean> permissions = PlanAccess.compute(plan.getStatus());
        if (!Boolean.TRUE.equals(permissions.get(action))) {
            throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                    + plan.getStatus() + "，允许：" + allowedActions(permissions) + "）",
                    plan.getStatus(), allowedActions(permissions));
        }
        return plan;
    }

    private void requireStatus(PersistentTaskPlanRecord plan, PlanStatus expected, String action) {
        if (plan.getStatus() != expected) {
            Map<String, Boolean> permissions = PlanAccess.compute(plan.getStatus());
            throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                    + plan.getStatus() + "，允许：" + allowedActions(permissions) + "）",
                    plan.getStatus(), allowedActions(permissions));
        }
    }

    private List<String> allowedActions(java.util.Map<String, Boolean> permissions) {
        return permissions.entrySet().stream().filter(java.util.Map.Entry::getValue).map(java.util.Map.Entry::getKey).toList();
    }
}
