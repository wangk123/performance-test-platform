package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-version-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanVersionServiceTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal COLLEAGUE = new HumanPrincipal("member-b", java.util.Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal OUTSIDER = new HumanPrincipal("stranger", java.util.Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanVersionService versionService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "member-b", ProjectRole.MEMBER));
        PersistentTaskPlanRecord plan = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner"));
        plan.updateBody("## 一、背景\n\n初始内容\n");
        planId = planRepository.save(plan).getId();
    }

    @Test
    void firstPublishFreezesBodyWithMeta() {
        PlanVersionService.PlanVersionView view = versionService.publish(planId, OWNER, "V1.0", "首版发布");
        assertThat(view.versionNo()).isEqualTo("V1.0");
        assertThat(view.createdBy()).isEqualTo("owner");
        assertThat(view.author()).isEqualTo("owner");
        assertThat(view.planPhase()).isEqualTo("DRAFT");

        PlanVersionService.PlanVersionDetail detail = versionService.get(planId, view.id(), OWNER);
        assertThat(detail.snapshotBody()).contains("初始内容");
    }

    @Test
    void sameVersionNoOverwritesLatestKeepsCreatedBy() {
        PlanVersionService.PlanVersionView first = versionService.publish(planId, OWNER, "V1.0", "首版");
        planRepository.findById(planId).ifPresent(plan -> {
            plan.updateBody("## 一、背景\n\n小修错别字\n");
            planRepository.save(plan);
        });

        PlanVersionService.PlanVersionView overwritten =
                versionService.publish(planId, COLLEAGUE, "V1.0", "错别字小修，不升号");

        assertThat(overwritten.id()).isEqualTo(first.id());
        assertThat(overwritten.author()).isEqualTo("member-b");
        assertThat(overwritten.createdBy()).isEqualTo("owner");
        assertThat(overwritten.changeNote()).isEqualTo("错别字小修，不升号");
        assertThat(overwritten.createdAt()).isEqualTo(first.createdAt());
        assertThat(overwritten.updatedAt()).isAfterOrEqualTo(first.updatedAt());
        PlanVersionService.PlanVersionDetail afterOverwrite = versionService.get(planId, first.id(), OWNER);
        assertThat(afterOverwrite.snapshotBody()).contains("小修错别字");
        PlanVersionService.PlanVersionListResponse list = versionService.list(planId, OWNER);
        assertThat(list.versions()).hasSize(1);
        assertThat(list.bodyDiffersFromLatest()).isFalse();
    }

    @Test
    void lowerThanLatestRejectedIncludingSegmentCompare() {
        versionService.publish(planId, OWNER, "V1.10", "先发高版本");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.9", "倒退"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_NOT_LATEST");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.0", "更早"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_NOT_LATEST");
    }

    @Test
    void duplicateNonLatestFreeTextRejected() {
        versionService.publish(planId, OWNER, "final", "首版");
        versionService.publish(planId, OWNER, "V2.0", "升版");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "final", "重号"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_DUPLICATE");
    }

    @Test
    void blankVersionNoOrNoteRejected() {
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, " ", "note"))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_INVALID");
        assertThatThrownBy(() -> versionService.publish(planId, OWNER, "V1.0", " "))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("PLAN_VERSION_INVALID");
    }

    @Test
    void listOrdersByCreatedDescAndReportsDirtyFlag() {
        versionService.publish(planId, OWNER, "V1.0", "首版");
        planRepository.findById(planId).ifPresent(plan -> {
            plan.updateBody("## 一、背景\n\n改动未发布\n");
            planRepository.save(plan);
        });
        PlanVersionService.PlanVersionListResponse list = versionService.list(planId, OWNER);
        assertThat(list.versions()).extracting(PlanVersionService.PlanVersionView::versionNo)
                .containsExactly("V1.0");
        assertThat(list.bodyDiffersFromLatest()).isTrue();
    }

    @Test
    void nonProjectMemberDenied() {
        assertThatThrownBy(() -> versionService.list(planId, OUTSIDER))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void manualPublishRecordsManualKind() {
        PlanVersionService.PlanVersionView view = versionService.publish(planId, OWNER, "V1.0", "手动发版");
        assertThat(view.kind()).isEqualTo(PersistentPlanVersionRecord.KIND_MANUAL);
    }

    @Test
    void workflowPublishRecordsPublishKindAndTruncatesLongNote() {
        PlanVersionService.PlanVersionView view =
                versionService.publishForWorkflow(planId, OWNER, "V1.0", "结".repeat(1200));
        assertThat(view.kind()).isEqualTo(PersistentPlanVersionRecord.KIND_PUBLISH);
        assertThat(view.changeNote()).hasSize(998); // 997 字 + 省略号
        assertThat(view.changeNote()).endsWith("…");
    }

    @Test
    void listOrdersMultipleVersionsByCreatedDesc() {
        versionService.publish(planId, OWNER, "V1.0", "首版");
        versionService.publish(planId, OWNER, "V1.1", "补充");
        versionService.publish(planId, OWNER, "V1.2", "再补充");
        assertThat(versionService.list(planId, OWNER).versions())
                .extracting(PlanVersionService.PlanVersionView::versionNo)
                .containsExactly("V1.2", "V1.1", "V1.0");
    }

    @Test
    void publishDeniedForNonProjectMember() {
        assertThatThrownBy(() -> versionService.publish(planId, OUTSIDER, "V1.0", "越权"))
                .isInstanceOf(PlanAccessDeniedException.class);
    }

    @Test
    void compareVersionNumbersSegmentsAndIncomparable() {
        assertThat(PlanVersionService.compareVersionNumbers("V1.9", "V1.10")).isNegative();
        assertThat(PlanVersionService.compareVersionNumbers("v2.0", "V1.9")).isPositive();
        assertThat(PlanVersionService.compareVersionNumbers("V1", "1.0.0")).isZero();
        assertThat(PlanVersionService.compareVersionNumbers("final", "V1.0")).isNull();
    }
}
