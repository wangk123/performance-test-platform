package com.yr.perftest.platform.task.plandoc;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.PersistentProjectMemberRecord;
import com.yr.perftest.platform.project.PersistentProjectMemberRepository;
import com.yr.perftest.platform.project.PersistentProjectRecord;
import com.yr.perftest.platform.project.PersistentProjectRepository;
import com.yr.perftest.platform.project.ProjectRole;
import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-template-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PlanTemplateTest {

    private static final HumanPrincipal OWNER = new HumanPrincipal("owner", Set.of(SystemRole.PROJECT_MEMBER));
    private static final HumanPrincipal MEMBER = new HumanPrincipal("member-b", Set.of(SystemRole.PROJECT_MEMBER));

    @Autowired
    private PlanWorkflowService workflow;
    @Autowired
    private TaskPlanService planService;
    @Autowired
    private PersistentTaskPlanRepository planRepository;
    @Autowired
    private PersistentProjectRepository projectRepository;
    @Autowired
    private PersistentProjectMemberRepository memberRepository;

    private long projectId;

    @BeforeEach
    void setUp() {
        PersistentProjectRecord project = projectRepository.save(
                new PersistentProjectRecord("P1", "项目一", "", "owner"));
        projectId = project.getId();
        memberRepository.save(new PersistentProjectMemberRecord(projectId, "owner", ProjectRole.OWNER));
        memberRepository.save(new PersistentProjectMemberRecord(projectId, "member-b", ProjectRole.MEMBER));
    }

    @Test
    void builtinTemplateSeededAndUntouchable() {
        var templates = workflow.listTemplates(projectId);
        assertThat(templates).anyMatch(t -> t.getName().equals("通用压测计划") && t.isBuiltin());
        Long builtinId = templates.stream().filter(t -> t.isBuiltin()).findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> workflow.updateTemplate(builtinId, OWNER, "改", null, "x"))
                .isInstanceOf(PlanValidationException.class);
        assertThatThrownBy(() -> workflow.deleteTemplate(builtinId, OWNER))
                .isInstanceOf(PlanValidationException.class);
    }

    @Test
    void templateManagementRestrictedToOwner() {
        assertThatThrownBy(() -> workflow.createTemplate(projectId, MEMBER, "t", null, "# 内容"))
                .isInstanceOf(PlanAccessDeniedException.class);
        var created = workflow.createTemplate(projectId, OWNER, "项目模板", "描述", "## 一、背景\n\n{{planName}}\n");
        assertThat(workflow.listTemplates(projectId)).anyMatch(t -> t.getName().equals("项目模板"));
        workflow.updateTemplate(created.getId(), OWNER, "项目模板2", null, "## 一、背景\n\n改\n");
        workflow.deleteTemplate(created.getId(), OWNER);
        assertThat(workflow.listTemplates(projectId)).noneMatch(t -> t.getName().equals("项目模板2"));
    }

    @Test
    void createPlanWithTemplateRendersBodyAndDefaultPrecheck() {
        Long templateId = workflow.listTemplates(projectId).stream()
                .filter(t -> t.getName().equals("通用压测计划")).findFirst().orElseThrow().getId();
        TaskPlan plan = planService.createPlan(projectId, "零售3.1 压测", null, null, null, null, "owner", templateId);
        assertThat(plan.body()).contains("# 零售3.1 压测 性能测试计划");
        assertThat(plan.body()).contains("## 二、测试目的与指标");
        assertThat(plan.body()).contains("## 十一、结论");
        assertThat(plan.body()).contains("指标已定义（自动）");
        assertThat(plan.revision()).isEqualTo(1);
        // 默认 precheck：disabled + 默认清单（入口准则条目）
        com.yr.perftest.platform.task.PersistentTaskPlanRecord raw = planRepository.findById(plan.id()).orElseThrow();
        assertThat(raw.getPrecheckJson()).contains("\"enabled\":false");
        assertThat(raw.getPrecheckJson()).contains("指标已定义");
    }
}
