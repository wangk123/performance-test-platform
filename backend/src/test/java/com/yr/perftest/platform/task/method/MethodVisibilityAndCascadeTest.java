package com.yr.perftest.platform.task.method;

import com.yr.perftest.platform.api.TaskPlanController;
import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRecord;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.task.PersistentTaskPlanRecord;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 5：执行行可见性 PATCH（表格移出/恢复）与彻底删除同事务级联删图。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:method-visibility-cascade-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.h2.console.enabled=false",
        "platform.storage.root=./build/test-storage/method-visibility-cascade"
})
@Transactional
class MethodVisibilityAndCascadeTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("mv-owner", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private TaskPlanController controller;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;
    @Autowired
    private PlanEvidenceImageRepository imageRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long planId;
    private long scenarioId;
    private long executionId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OWNER, null, List.of()));

        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P-MV", "可见性与级联", "", "mv-owner"));
        memberRepository.save(new PersistentProjectMemberRecord(project.getId(), "mv-owner", ProjectRole.OWNER));
        planId = planRepository.save(
                new PersistentTaskPlanRecord(project.getId(), "可见性计划", null, "mv-owner")).getId();
        scenarioId = scenarioRepository.save(
                new PersistentTaskScenarioRecord(planId, null, "可见性场景", 0)).getId();

        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId,
                        "{\"threads\":10,\"rampUp\":1,\"duration\":5,\"loops\":1,\"jmeterProperties\":{}}"));
        execution.markSuccess(0); // 终态才可删
        executionId = executionRepository.save(execution).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patchTogglesMethodHidden() {
        assertThat(executionRepository.findById(executionId).orElseThrow().isMethodHidden()).isFalse();

        controller.setMethodVisibility(executionId, new MethodSectionService.VisibilityRequest(true));
        assertThat(executionRepository.findById(executionId).orElseThrow().isMethodHidden()).isTrue();

        controller.setMethodVisibility(executionId, new MethodSectionService.VisibilityRequest(false));
        assertThat(executionRepository.findById(executionId).orElseThrow().isMethodHidden()).isFalse();
    }

    @Test
    void batchDeleteRemovesEvidenceImages() {
        long imgId = imageRepository.save(new PersistentPlanEvidenceImageRecord(
                planId, scenarioId, "GC 截图", 0,
                "storage/evidence/gc.png", "image/png", 1024, "mv-owner")).getId();
        PersistentPlanEvidenceImageRecord image = imageRepository.findById(imgId).orElseThrow();
        image.setExecutionId(executionId);
        imageRepository.save(image);

        controller.deleteExecutions(List.of(executionId));

        assertThat(executionRepository.findById(executionId)).isEmpty();
        assertThat(imageRepository.findById(imgId)).isEmpty();
    }
}
