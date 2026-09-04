package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;

import java.util.LinkedHashMap;
import java.util.Map;

/** 动作可见性矩阵（设计 §13.2 + §4.4 + §4.5）。键集合见 Global Constraints。 */
public final class PlanAccess {

    private PlanAccess() {
    }

    public static Map<String, Boolean> compute(PlanActorRole role, PlanPhase phase, PlanStatus status, boolean hasAnyExecution) {
        boolean ownerLike = role == PlanActorRole.SYSTEM_ADMIN
                || role == PlanActorRole.PROJECT_OWNER
                || role == PlanActorRole.PLAN_OWNER;
        boolean memberLike = ownerLike || role == PlanActorRole.MEMBER;
        boolean editable = ownerLike && phase == PlanPhase.DRAFT;
        boolean frozen = phase == PlanPhase.PUBLISH;

        Map<String, Boolean> p = new LinkedHashMap<>();
        p.put("EDIT", editable);
        p.put("SUBMIT", ownerLike && phase == PlanPhase.DRAFT);
        p.put("START_REVIEW", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.PENDING);
        p.put("APPROVE", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.IN_REVIEW);
        p.put("REJECT", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.IN_REVIEW);
        p.put("WITHDRAW", ownerLike && phase == PlanPhase.REVIEW && (status == PlanStatus.PENDING || status == PlanStatus.IN_REVIEW));
        p.put("BACK_TO_DRAFT", ownerLike && !hasAnyExecution
                && ((phase == PlanPhase.REVIEW && status == PlanStatus.APPROVED)
                || (phase == PlanPhase.EXECUTION && status == PlanStatus.PENDING)));
        p.put("START_EXECUTION", memberLike && phase == PlanPhase.REVIEW && status == PlanStatus.APPROVED);
        p.put("TO_REPORT", memberLike && phase == PlanPhase.EXECUTION && status == PlanStatus.DONE);
        p.put("GENERATE_REPORT", memberLike && phase == PlanPhase.REPORT && (status == PlanStatus.PENDING || status == PlanStatus.DONE));
        p.put("PUBLISH", ownerLike && phase == PlanPhase.REPORT && status == PlanStatus.DONE);
        p.put("NEW_REVISION", ownerLike && frozen);
        p.put("PRECHECK_RUN", memberLike && !frozen);
        p.put("PRECHECK_SKIP", memberLike && !frozen);
        p.put("DELETE", ownerLike);
        p.put("COMMENT", memberLike && (phase == PlanPhase.DRAFT || phase == PlanPhase.REVIEW));
        p.put("SHARE", ownerLike && frozen);
        return p;
    }
}
