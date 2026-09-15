package com.yr.perftest.platform.api;

import com.yr.perftest.platform.envcheck.EnvCheckItem;
import com.yr.perftest.platform.envcheck.EnvCheckRegistry;
import com.yr.perftest.platform.envcheck.EnvCheckRunService;
import com.yr.perftest.platform.envcheck.RemoteCheckItem;
import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.task.plandoc.PlanWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final EnvCheckRegistry registry;
    private final EnvCheckRunService runService;
    private final PlanWorkflowService workflowService;

    public EnvCheckController(EnvCheckRegistry registry, EnvCheckRunService runService,
                              PlanWorkflowService workflowService) {
        this.registry = registry;
        this.runService = runService;
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
