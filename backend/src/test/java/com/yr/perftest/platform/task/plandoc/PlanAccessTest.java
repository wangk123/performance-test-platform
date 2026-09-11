package com.yr.perftest.platform.task.plandoc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 无角色维度：动作可见性只由单一状态决定（spec §4.1/§4.4/§10）。 */
class PlanAccessTest {

    private Map<String, Boolean> of(PlanStatus status) {
        return PlanAccess.compute(status);
    }

    @Test
    void planningOnlyAllowsSubmit() {
        Map<String, Boolean> p = of(PlanStatus.PLANNING);
        assertThat(p.get("SUBMIT")).isTrue();
        assertThat(p.get("APPROVE")).isFalse();
        assertThat(p.get("FINISH")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
    }

    @Test
    void inReviewOnlyAllowsApprove() {
        Map<String, Boolean> p = of(PlanStatus.IN_REVIEW);
        assertThat(p.get("APPROVE")).isTrue();
        assertThat(p.get("SUBMIT")).isFalse();
        assertThat(p.get("FINISH")).isFalse();
    }

    @Test
    void executingOnlyAllowsFinish() {
        Map<String, Boolean> p = of(PlanStatus.EXECUTING);
        assertThat(p.get("FINISH")).isTrue();
        assertThat(p.get("APPROVE")).isFalse();
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("EXECUTE")).isTrue();
    }

    @Test
    void reportingAllowsPublishAndExecute() {
        Map<String, Boolean> p = of(PlanStatus.REPORTING);
        assertThat(p.get("PUBLISH")).isTrue();
        assertThat(p.get("FINISH")).isFalse();
        assertThat(p.get("EXECUTE")).isTrue(); // 报告阶段支持复测
    }

    @Test
    void publishedIsTerminalButUnfrozen() {
        Map<String, Boolean> p = of(PlanStatus.PUBLISHED);
        assertThat(p.get("PUBLISH")).isFalse();
        assertThat(p.get("SHARE")).isTrue();
        assertThat(p.get("EDIT")).isTrue();       // 发布后不冻结文档
        assertThat(p.get("NEW_VERSION")).isTrue();
    }

    @Test
    void globalActionsAvailableInEveryStatus() {
        for (PlanStatus status : PlanStatus.values()) {
            Map<String, Boolean> p = of(status);
            assertThat(p.get("EDIT")).as("EDIT in %s", status).isTrue();
            assertThat(p.get("COMMENT")).as("COMMENT in %s", status).isTrue();
            assertThat(p.get("NEW_VERSION")).as("NEW_VERSION in %s", status).isTrue();
            assertThat(p.get("DELETE")).as("DELETE in %s", status).isTrue();
            assertThat(p.get("PRECHECK_RUN")).as("PRECHECK_RUN in %s", status).isTrue();
            assertThat(p.get("PRECHECK_SKIP")).as("PRECHECK_SKIP in %s", status).isTrue();
        }
    }

    @Test
    void executeForbiddenBeforeApproved() {
        assertThat(of(PlanStatus.PLANNING).get("EXECUTE")).isFalse();
        assertThat(of(PlanStatus.IN_REVIEW).get("EXECUTE")).isFalse();
    }
}
