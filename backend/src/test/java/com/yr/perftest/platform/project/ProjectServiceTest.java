package com.yr.perftest.platform.project;

import com.yr.perftest.platform.TestSupport;

import java.util.List;

public final class ProjectServiceTest {
    private ProjectServiceTest() {
    }

    public static void runAll() {
        createsProjectAndRejectsDuplicateCode();
        hidesArchivedProjectsFromTaskSelection();
        enforcesProjectMemberAccess();
    }

    private static void createsProjectAndRejectsDuplicateCode() {
        ProjectService service = new ProjectService();

        Project project = service.createProject("loan-perf", "贷款系统压测", "核心链路压测", "admin");

        TestSupport.assertEquals("loan-perf", project.getCode(), "project code");
        TestSupport.assertEquals(ProjectStatus.ACTIVE, project.getStatus(), "new project should be active");
        TestSupport.assertThrows(ProjectValidationException.class, new TestSupport.ThrowingRunnable() {
            @Override
            public void run() {
                service.createProject("loan-perf", "重复项目", "duplicate", "admin");
            }
        }, "duplicate project code should fail");
    }

    private static void hidesArchivedProjectsFromTaskSelection() {
        ProjectService service = new ProjectService();
        Project active = service.createProject("payment", "支付系统压测", "支付链路", "admin");
        Project archived = service.createProject("legacy", "历史系统压测", "历史系统", "admin");

        service.archiveProject(archived.getId(), "admin");

        List<Project> selectableProjects = service.listProjectsAvailableForTaskSelection();

        TestSupport.assertTrue(selectableProjects.contains(active), "active project should be selectable");
        TestSupport.assertFalse(selectableProjects.contains(archived), "archived project should not be selectable");
    }

    private static void enforcesProjectMemberAccess() {
        ProjectService service = new ProjectService();
        Project project = service.createProject("risk", "风控系统压测", "风控链路", "owner");

        service.addMember(project.getId(), "engineer", ProjectRole.MEMBER, "owner");

        TestSupport.assertTrue(service.canAccessProject(project.getId(), "owner"), "owner should access project");
        TestSupport.assertTrue(service.canAccessProject(project.getId(), "engineer"), "member should access project");
        TestSupport.assertFalse(service.canAccessProject(project.getId(), "outsider"), "outsider should not access project");
    }
}
