package com.yr.perftest.platform.api;

import com.yr.perftest.platform.envcheck.EnvCheckItem;
import com.yr.perftest.platform.envcheck.EnvCheckFixService;
import com.yr.perftest.platform.envcheck.EnvCheckRegistry;
import com.yr.perftest.platform.envcheck.EnvCheckRunService;
import com.yr.perftest.platform.envcheck.PersistentEnvCheckFixRecord;
import com.yr.perftest.platform.envcheck.RemoteCheckItem;
import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** 环境检查 API：检查项目录 + 运行触发/历史/详情。 */
@RestController
@RequestMapping("/api")
public class EnvCheckController {

    /** 检查项目录视图：fixable 仅 REMOTE 项可能为 true；LOCAL 项 risk 为 null。 */
    public record ItemView(String key, String label, String description, String category, String kind,
                           Set<String> appliesTo, boolean fixable, String risk, int sortOrder) {
    }

    /** 批量修复请求体：{"requests":[{host,itemKey}]}。 */
    public record FixBatchRequest(List<EnvCheckFixService.FixRequest> requests) {
    }

    private final EnvCheckRegistry registry;
    private final EnvCheckRunService runService;
    private final EnvCheckFixService fixService;
    private final PlanWorkflowService workflowService;

    public EnvCheckController(EnvCheckRegistry registry, EnvCheckRunService runService,
                              EnvCheckFixService fixService, PlanWorkflowService workflowService) {
        this.registry = registry;
        this.runService = runService;
        this.fixService = fixService;
        this.workflowService = workflowService;
    }

    @GetMapping("/env-check/items")
    public List<ItemView> items() {
        return registry.all().stream().map(this::toView).toList();
    }

    @PostMapping("/task-plans/{planId}/env-check/runs")
    public EnvCheckRunService.RunSummary trigger(@PathVariable long planId) {
        HumanPrincipal actor = requireHuman();
        workflowService.requireActor(planId, actor, "PRECHECK_RUN");
        return runService.toSummary(runService.trigger(planId, actor.username()));
    }

    @GetMapping("/task-plans/{planId}/env-check/runs")
    public List<EnvCheckRunService.RunSummary> history(@PathVariable long planId) {
        workflowService.requireActor(planId, requireHuman(), "PRECHECK_RUN");
        return runService.history(planId).stream().map(runService::toSummary).toList();
    }

    @GetMapping("/env-check/runs/{runId}")
    public EnvCheckRunService.RunDetail detail(@PathVariable long runId) {
        EnvCheckRunService.RunDetail detail = runService.detail(runId);
        workflowService.requireActor(detail.run().getPlanId(), requireHuman(), "PRECHECK_RUN");
        return detail;
    }

    /** 批量修复（spec §4.5）：逐项重探→备份→修→复查，返回 fixed/skipped/failed 分流。 */
    @PostMapping("/env-check/runs/{runId}/fixes")
    public EnvCheckFixService.FixOutcome applyFixes(@PathVariable long runId, @RequestBody FixBatchRequest request) {
        HumanPrincipal actor = requireHuman();
        workflowService.requireActor(runService.detail(runId).run().getPlanId(), actor, "PRECHECK_RUN");
        return fixService.apply(runId, request.requests(), actor.username());
    }

    /** 回滚单项修复：恢复备份原值，结果行退回 WARNING，系统批注留痕。 */
    @PostMapping("/env-check/fixes/{fixId}/rollback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollbackFix(@PathVariable long fixId) {
        HumanPrincipal actor = requireHuman();
        PersistentEnvCheckFixRecord fix = fixService.requireFix(fixId);
        workflowService.requireActor(runService.detail(fix.getRunId()).run().getPlanId(), actor, "PRECHECK_RUN");
        fixService.rollback(fixId, actor.username());
    }

    private ItemView toView(EnvCheckItem item) {
        if (item instanceof RemoteCheckItem remoteItem) {
            return new ItemView(item.key(), item.label(), item.description(), item.category().name(),
                    item.kind().name(), item.appliesTo(), remoteItem.fixable(),
                    remoteItem.risk().name(), item.sortOrder());
        }
        return new ItemView(item.key(), item.label(), item.description(), item.category().name(),
                item.kind().name(), item.appliesTo(), false, null, item.sortOrder());
    }

    private HumanPrincipal requireHuman() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HumanPrincipal human) {
            return human;
        }
        throw new AuthenticationException("login required");
    }
}
