package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-comment-anchor-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanCommentAnchorTest {

    @Autowired
    private PersistentPlanCommentRepository commentRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;
    @Autowired
    private PersistentTaskPlanRepository planRepository;

    private long planId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "owner", ProjectRole.OWNER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "计划一", null, "owner")).getId();
    }

    @Test
    void anchoredRootRoundTrip() {
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "目标值偏乐观", PlanCommentKind.REVIEW,
                null, 42, "登录接口 TPS ≥ 1000", "三、测试指标", 5L));
        PersistentPlanCommentRecord loaded = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getParentId()).isNull();
        assertThat(loaded.getAnchorLine()).isEqualTo(42);
        assertThat(loaded.getAnchorText()).isEqualTo("登录接口 TPS ≥ 1000");
        assertThat(loaded.getSectionTitle()).isEqualTo("三、测试指标");
        assertThat(loaded.getBodyRevision()).isEqualTo(5L);
        assertThat(loaded.isResolved()).isFalse();
    }

    @Test
    void resolveMutationPersists() {
        PersistentPlanCommentRecord saved = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "口径不清", PlanCommentKind.REVIEW, null, 7, "下单接口", "三、测试指标", 5L));
        saved.applyResolve(true, "owner");
        commentRepository.save(saved);
        PersistentPlanCommentRecord loaded = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.isResolved()).isTrue();
        assertThat(loaded.getResolvedBy()).isEqualTo("owner");
        assertThat(loaded.getResolvedAt()).isNotNull();
        loaded.applyResolve(false, "owner");
        commentRepository.save(loaded);
        PersistentPlanCommentRecord reopened = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reopened.isResolved()).isFalse();
        assertThat(reopened.getResolvedBy()).isNull();
        assertThat(reopened.getResolvedAt()).isNull();
    }

    @Test
    void findRepliesByParent() {
        PersistentPlanCommentRecord root = commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "根批注", PlanCommentKind.REVIEW, null, 7, "下单接口", "三、测试指标", 5L));
        commentRepository.save(new PersistentPlanCommentRecord(
                planId, "owner", "回复一", PlanCommentKind.REVIEW, root.getId(), null, null, null, null));
        commentRepository.save(new PersistentPlanCommentRecord(
                planId, "reviewer", "回复二", PlanCommentKind.REVIEW, root.getId(), null, null, null, null));
        assertThat(commentRepository.findAllByParentId(root.getId())).hasSize(2);
    }
}
