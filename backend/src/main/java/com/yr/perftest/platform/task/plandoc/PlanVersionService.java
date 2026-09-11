package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.project.ProjectAccessResolver;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 计划版本发布与修订记录（spec 2026-09-10）。
 * 发布规则：版本号=最新 → 覆盖最新（前端已二次确认）；数值低于最新 → 拒绝；
 * 重号但非最新 → 拒绝（覆盖仅限最新版本）；其余 → 新建。
 * 权限：项目成员即可读；发布沿用同口径（spec「任意阶段·EDIT 即可」，不按阶段收敛）。
 */
@Service
public class PlanVersionService {
    private final PersistentTaskPlanRepository planRepository;
    private final PersistentPlanVersionRepository versionRepository;
    private final ProjectAccessResolver accessResolver;

    public PlanVersionService(PersistentTaskPlanRepository planRepository,
                              PersistentPlanVersionRepository versionRepository,
                              ProjectAccessResolver accessResolver) {
        this.planRepository = planRepository;
        this.versionRepository = versionRepository;
        this.accessResolver = accessResolver;
    }

    public record PlanVersionView(long id, String versionNo, String changeNote, String createdBy, String author,
                                  String planPhase, int planRevision, String kind, Instant createdAt, Instant updatedAt) {
    }

    public record PlanVersionDetail(long id, String versionNo, String changeNote, String createdBy, String author,
                                    String planPhase, int planRevision, String kind, Instant createdAt, Instant updatedAt,
                                    String snapshotBody) {
    }

    public record PlanVersionListResponse(List<PlanVersionView> versions, boolean bodyDiffersFromLatest) {
    }

    /** 手动发版入口（版本 Tab / MCP 后续）。 */
    @Transactional
    public PlanVersionView publish(long planId, HumanPrincipal actor, String versionNo, String changeNote) {
        return doPublish(planId, actor, versionNo, changeNote, PersistentPlanVersionRecord.KIND_MANUAL);
    }

    /** 报告发布登记（spec §9）：工作流 publish 转换在 applyPublish 之后调用，kind=PUBLISH。 */
    @Transactional
    public PlanVersionView publishForWorkflow(long planId, HumanPrincipal actor, String versionNo, String changeNote) {
        return doPublish(planId, actor, versionNo, changeNote, PersistentPlanVersionRecord.KIND_PUBLISH);
    }

    private PlanVersionView doPublish(long planId, HumanPrincipal actor, String versionNo, String changeNote,
                                      String kind) {
        PersistentTaskPlanRecord plan = planRepository.findWithLockingById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
        requireReadable(plan, actor);
        String no = versionNo == null ? "" : versionNo.trim();
        String note = changeNote == null ? "" : changeNote.trim();
        if (no.isEmpty()) {
            throw new PlanValidationException("PLAN_VERSION_INVALID：版本号不能为空");
        }
        if (note.isEmpty()) {
            throw new PlanValidationException("PLAN_VERSION_INVALID：变更内容不能为空");
        }
        if (note.length() > 1000) {
            note = note.substring(0, 997) + "…"; // 发布结论可超长，修订记录截断保底
        }
        List<PersistentPlanVersionRecord> existing = versionRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId);
        if (!existing.isEmpty()) {
            PersistentPlanVersionRecord latest = existing.get(0);
            if (latest.getVersionNo().equals(no)) {
                latest.applyOverwrite(plan.getBody() == null ? "" : plan.getBody(), note, actor.username(),
                        plan.getPhase().name(), plan.getRevision(), Instant.now());
                return toView(versionRepository.save(latest));
            }
            Integer comparison = compareVersionNumbers(no, latest.getVersionNo());
            if (comparison != null && comparison < 0) {
                throw new PlanValidationException("PLAN_VERSION_NOT_LATEST：版本号低于最新版本 "
                        + latest.getVersionNo() + "，不允许提交");
            }
            if (versionRepository.existsByPlanIdAndVersionNo(planId, no)) {
                throw new PlanValidationException("PLAN_VERSION_DUPLICATE：版本号已被历史版本使用，仅最新版本可覆盖更新");
            }
        }
        PersistentPlanVersionRecord created = new PersistentPlanVersionRecord(planId, no, note,
                actor.username(), actor.username(), plan.getBody() == null ? "" : plan.getBody(),
                plan.getPhase().name(), plan.getRevision(), kind, Instant.now());
        return toView(versionRepository.save(created));
    }

    @Transactional(readOnly = true)
    public PlanVersionListResponse list(long planId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireReadable(plan, actor);
        List<PersistentPlanVersionRecord> versions = versionRepository.findByPlanIdOrderByCreatedAtDescIdDesc(planId);
        boolean dirty = false;
        if (!versions.isEmpty()) {
            String latestBody = versions.get(0).getSnapshotBody();
            dirty = !latestBody.equals(plan.getBody() == null ? "" : plan.getBody());
        }
        return new PlanVersionListResponse(versions.stream().map(this::toView).toList(), dirty);
    }

    @Transactional(readOnly = true)
    public PlanVersionDetail get(long planId, long versionId, HumanPrincipal actor) {
        PersistentTaskPlanRecord plan = requirePlan(planId);
        requireReadable(plan, actor);
        PersistentPlanVersionRecord version = versionRepository.findById(versionId)
                .filter(v -> v.getPlanId() == planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_VERSION_INVALID：版本不存在"));
        return new PlanVersionDetail(version.getId(), version.getVersionNo(), version.getChangeNote(),
                version.getCreatedBy(), version.getAuthor(), version.getPlanPhase(), version.getPlanRevision(),
                version.getKind(), version.getCreatedAt(), version.getUpdatedAt(), version.getSnapshotBody());
    }

    /** 数值版本比较：`v?数字[.数字]*` 逐段比较（缺段补 0，V1 == 1.0）；任一侧不合规返回 null（不可比）。 */
    static Integer compareVersionNumbers(String left, String right) {
        int[] a = parseSegments(left);
        int[] b = parseSegments(right);
        if (a == null || b == null) {
            return null;
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] parseSegments(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split("\\.");
        int[] segments = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d+")) {
                return null;
            }
            segments[i] = Integer.parseInt(parts[i]);
        }
        return segments;
    }

    private PersistentTaskPlanRecord requirePlan(long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new PlanValidationException("PLAN_INVALID：task plan does not exist"));
    }

    private void requireReadable(PersistentTaskPlanRecord plan, HumanPrincipal actor) {
        ProjectAccessResolver.PlanActorRole role =
                accessResolver.resolve(plan.getProjectId(), actor, plan.getCreatedBy());
        if (role == ProjectAccessResolver.PlanActorRole.NONE) {
            throw new PlanAccessDeniedException("PLAN_ACCESS_DENIED：非项目成员");
        }
    }

    private PlanVersionView toView(PersistentPlanVersionRecord version) {
        return new PlanVersionView(version.getId(), version.getVersionNo(), version.getChangeNote(),
                version.getCreatedBy(), version.getAuthor(), version.getPlanPhase(), version.getPlanRevision(),
                version.getKind(), version.getCreatedAt(), version.getUpdatedAt());
    }
}
