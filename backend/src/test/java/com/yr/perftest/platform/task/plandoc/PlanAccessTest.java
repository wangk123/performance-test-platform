package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.ProjectAccessResolver.PlanActorRole;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanAccessTest {

    @Test
    void memberCanReviewAndExecuteButNotEditOrPublish() {
        Map<String, Boolean> p = PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.REVIEW, PlanStatus.IN_REVIEW, false);
        // START_REVIEW 的前置是 REVIEW/PENDING（设计 §4.4），与 APPROVE/REJECT（IN_REVIEW）互斥——brief 原断言状态有误，此处按设计修正。
        assertThat(PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.REVIEW, PlanStatus.PENDING, false).get("START_REVIEW")).isTrue();
        assertThat(p.get("APPROVE")).isTrue();
        assertThat(p.get("REJECT")).isTrue();
        assertThat(p.get("COMMENT")).isTrue();
        assertThat(p.get("EDIT")).isFalse();
        assertThat(p.get("SUBMIT")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("DELETE")).isFalse();
        assertThat(p.get("SHARE")).isFalse();
    }

    @Test
    void ownerCanEditOnlyInDraftPhase() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.DRAFT, PlanStatus.DRAFT, false).get("EDIT")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REVIEW, PlanStatus.PENDING, false).get("EDIT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.DONE, true).get("EDIT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("EDIT")).isFalse();
    }

    @Test
    void backToDraftRequiresNoExecution() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.PENDING, false).get("BACK_TO_DRAFT")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.EXECUTION, PlanStatus.PENDING, true).get("BACK_TO_DRAFT")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REVIEW, PlanStatus.APPROVED, false).get("BACK_TO_DRAFT")).isTrue();
    }

    @Test
    void publishRequiresReportDone() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REPORT, PlanStatus.DONE, true).get("PUBLISH")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.REPORT, PlanStatus.PENDING, true).get("PUBLISH")).isFalse();
        assertThat(PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.REPORT, PlanStatus.DONE, true).get("PUBLISH")).isFalse();
    }

    @Test
    void newRevisionOnlyFromPublishedForOwner() {
        assertThat(PlanAccess.compute(PlanActorRole.PLAN_OWNER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("NEW_REVISION")).isTrue();
        assertThat(PlanAccess.compute(PlanActorRole.MEMBER, PlanPhase.PUBLISH, PlanStatus.PUBLISHED, true).get("NEW_REVISION")).isFalse();
    }

    @Test
    void nonMemberHasNothing() {
        Map<String, Boolean> p = PlanAccess.compute(PlanActorRole.NONE, PlanPhase.DRAFT, PlanStatus.DRAFT, false);
        assertThat(p.values()).allMatch(v -> !v);
    }
}
