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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 计划状态机流转与批注（设计 §4/§6）。报告/发布/分享/预检分任务追加；模板 CRUD 自本任务起。 */
@Service
public class PlanWorkflowService {

    public record CommentView(long id, long planId, String author, String content, PlanCommentKind kind, Instant createdAt) {
    }

    private final PersistentTaskPlanRepository planRepository;
    private final PersistentTaskScenarioRepository scenarioRepository;
    private final PersistentScenarioExecutionRepository executionRepository;
    private final PersistentPlanCommentRepository commentRepository;
    private final ProjectAccessResolver accessResolver;
    private final PersistentPlanTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final PlanDocumentService documentService;
    private final ExecutionQueryService executionQueryService;
    private final PersistentPlanShareTokenRepository shareTokenRepository;

    public PlanWorkflowService(
            PersistentTaskPlanRepository planRepository,
            PersistentTaskScenarioRepository scenarioRepository,
            PersistentScenarioExecutionRepository executionRepository,
            PersistentPlanCommentRepository commentRepository,
            ProjectAccessResolver accessResolver,
            PersistentPlanTemplateRepository templateRepository,
            ObjectMapper objectMapper,
            PlanDocumentService documentService,
            ExecutionQueryService executionQueryService,
            PersistentPlanShareTokenRepository shareTokenRepository
    ) {
        this.planRepository = planRepository;
        this.scenarioRepository = scenarioRepository;
        this.executionRepository = executionRepository;
        this.commentRepository = commentRepository;
        this.accessResolver = accessResolver;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
        this.documentService = documentService;
        this.executionQueryService = executionQueryService;
        this.shareTokenRepository = shareTokenRepository;
    }

    @Transactional
    public void submit(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "SUBMIT");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 提交评审");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void startReview(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_REVIEW");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.IN_REVIEW);
        systemComment(planId, actor.username() + " 开始评审");
    }

    @Transactional
    public void approve(long planId, HumanPrincipal actor, String comment) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "APPROVE");
        plan.transitionTo(PlanPhase.REVIEW, PlanStatus.APPROVED);
        systemComment(planId, "评审通过（审批人：" + actor.username() + "）");
        if (comment != null && !comment.isBlank()) {
            commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        }
    }

    @Transactional
    public void reject(long planId, HumanPrincipal actor, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：驳回必须附批注");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "REJECT");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        commentRepository.save(new PersistentPlanCommentRecord(planId, actor.username(), comment.trim(), PlanCommentKind.REVIEW));
        systemComment(planId, actor.username() + " 驳回，退回草稿");
    }

    @Transactional
    public void withdraw(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "WITHDRAW");
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 撤回评审，退回草稿");
    }

    @Transactional
    public void backToDraft(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "BACK_TO_DRAFT");
        if (hasAnyExecution(planId)) {
            throw new PlanStateException("PLAN_STATE：已产生执行，不可退回草稿（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), List.of("TO_REPORT", "GENERATE_REPORT"));
        }
        plan.transitionTo(PlanPhase.DRAFT, PlanStatus.DRAFT);
        systemComment(planId, actor.username() + " 退回草稿");
    }

    @Transactional
    public void startExecution(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "START_EXECUTION");
        plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入执行阶段");
    }

    @Transactional
    public void toReport(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "TO_REPORT");
        plan.transitionTo(PlanPhase.REPORT, PlanStatus.PENDING);
        systemComment(planId, actor.username() + " 进入报告阶段");
    }

    public record PrecheckReport(boolean ok, List<String> failures, List<String> autoPassed) {
    }

    /** 执行门禁（设计 §10.1）：阶段 + 脚本 + 首执行环境检查。返回 planId。 */
    @Transactional
    public long assertExecutionAllowed(long scenarioId) {
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：scenario does not exist"));
        PersistentTaskPlanRecord plan = requirePlan(scenario.getPlanId());
        boolean phaseOk = plan.getPhase() == PlanPhase.EXECUTION
                || (plan.getPhase() == PlanPhase.REPORT && plan.getStatus() != PlanStatus.GENERATING);
        if (!phaseOk) {
            throw new PlanStateException("PLAN_STATE：请先通过评审并进入执行阶段（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "）",
                    plan.getPhase(), plan.getStatus(), List.of("SUBMIT", "START_REVIEW", "APPROVE", "START_EXECUTION"));
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

    @Transactional
    public void onExecutionStarted(long planId) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (plan.getPhase() == PlanPhase.REPORT) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING); // 报告作废（设计 §4.3）
        } else if (plan.getPhase() == PlanPhase.EXECUTION
                && (plan.getStatus() == PlanStatus.PENDING || plan.getStatus() == PlanStatus.DONE)) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.RUNNING);
        }
    }

    /** 终态联动：回填执行摘要 + 全部结束时置 DONE（设计 §4.3/§8）。 */
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
        if (plan.getPhase() == PlanPhase.EXECUTION && plan.getStatus() == PlanStatus.RUNNING
                && !documentService.hasActiveExecution(plan.getId())) {
            plan.transitionTo(PlanPhase.EXECUTION, PlanStatus.DONE);
        }
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
                    pass = PlanMarkdownSupport.extractSection(body, "二、测试目的与指标") != null
                            && PlanMarkdownSupport.extractSection(body, "二、测试目的与指标").contains("|---");
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
        String constraints = PlanMarkdownSupport.extractSection(body, "五、测试约束");
        if (constraints == null) {
            return;
        }
        String updated = constraints;
        for (String item : autoPassed) {
            updated = updated.replace("- [ ] " + item + "（自动）", "- [x] " + item + "（自动）");
        }
        if (!updated.equals(constraints)) {
            plan.updateBody(PlanMarkdownSupport.replaceSection(body, "五、测试约束", updated));
        }
    }

    @Transactional
    public void precheckSkip(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        plan.markPrecheckExecuted(Instant.now());
        systemComment(planId, "跳过环境检查继续执行（操作人：" + (actor == null ? "?" : actor.username()) + "）");
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
    public List<CommentView> listComments(long planId) {
        return commentRepository.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(c -> new CommentView(c.getId(), c.getPlanId(), c.getAuthor(), c.getContent(), c.getKind(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CommentView addComment(long planId, HumanPrincipal actor, String content) {
        if (content == null || content.isBlank()) {
            throw new PlanValidationException("PLAN_INVALID：批注内容不能为空");
        }
        PersistentTaskPlanRecord plan = requireActor(planId, actor, "COMMENT");
        PersistentPlanCommentRecord saved = commentRepository.save(
                new PersistentPlanCommentRecord(planId, actor.username(), content.trim(), PlanCommentKind.REVIEW));
        return new CommentView(saved.getId(), saved.getPlanId(), saved.getAuthor(), saved.getContent(), saved.getKind(), saved.getCreatedAt());
    }

    @Transactional
    public void deleteComment(long planId, long commentId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        PersistentPlanCommentRecord comment = commentRepository.findById(commentId)
                .filter(c -> c.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：批注不存在"));
        if (comment.getKind() == PlanCommentKind.SYSTEM) {
            throw new PlanValidationException("PLAN_INVALID：系统批注不可删除");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        boolean ownerLike = role == ProjectAccessResolver.PlanActorRole.SYSTEM_ADMIN
                || role == ProjectAccessResolver.PlanActorRole.PROJECT_OWNER
                || role == ProjectAccessResolver.PlanActorRole.PLAN_OWNER;
        if (!ownerLike && !comment.getAuthor().equals(actor.username())) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：仅批注作者/负责人/项目 OWNER/系统管理员可删除批注");
        }
        commentRepository.delete(comment);
    }

    @Transactional
    public void systemComment(long planId, String content) {
        commentRepository.save(new PersistentPlanCommentRecord(planId, "system", content, PlanCommentKind.SYSTEM));
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
        if (plan.getPhase() != PlanPhase.PUBLISH) {
            throw new PlanValidationException("SHARE_NOT_FOUND：分享链接不存在");
        }
        return new SharedPlanView(plan.getName(), plan.getBody(), plan.getPublishedAt());
    }

    private ShareView toShareView(PersistentPlanShareTokenRecord record) {
        return new ShareView(record.getId(), record.getPlanId(), record.getToken(),
                record.getExpiresAt(), record.getRevokedAt(), record.getCreatedBy(), record.getCreatedAt());
    }

    public boolean hasAnyExecution(long planId) {
        return scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(planId).stream()
                .anyMatch(scenario -> executionRepository.existsByScenarioId(scenario.getId()));
    }

    public PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    /** 校验动作权限并返回计划记录；角色不足抛 403，角色可而状态不允许抛 409（附允许动作）。 */
    private PersistentTaskPlanRecord requireActor(long planId, HumanPrincipal actor, String action) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        if (actor == null) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：未登录");
        }
        ProjectAccessResolver.PlanActorRole role = accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        java.util.Map<String, Boolean> permissions = PlanAccess.compute(role, plan.getPhase(), plan.getStatus(), hasAnyExecution(planId));
        if (!Boolean.TRUE.equals(permissions.get(action))) {
            if (role == ProjectAccessResolver.PlanActorRole.NONE) {
                throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
            }
            // 同状态下高权限角色可执行 → 属角色不足（403）；否则属状态不允许（409），与 PlanDocumentService 口径一致
            java.util.Map<String, Boolean> privileged = PlanAccess.compute(
                    ProjectAccessResolver.PlanActorRole.PLAN_OWNER, plan.getPhase(), plan.getStatus(), hasAnyExecution(planId));
            if (Boolean.TRUE.equals(privileged.get(action))) {
                throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：当前角色无权执行「" + action + "」");
            }
            throw new PlanStateException("PLAN_STATE：当前状态不允许「" + action + "」（当前 "
                    + plan.getPhase() + "/" + plan.getStatus() + "，允许："
                    + allowedActions(permissions) + "）",
                    plan.getPhase(), plan.getStatus(), allowedActions(permissions));
        }
        return plan;
    }

    private List<String> allowedActions(java.util.Map<String, Boolean> permissions) {
        return permissions.entrySet().stream().filter(java.util.Map.Entry::getValue).map(java.util.Map.Entry::getKey).toList();
    }
}
