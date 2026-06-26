package com.yr.perftest.platform.api;

import com.yr.perftest.platform.task.PersistentTaskPlanRepository;
import com.yr.perftest.platform.project.Project;
import com.yr.perftest.platform.project.ProjectOperations;
import com.yr.perftest.platform.project.ProjectStatus;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final ProjectOperations projectService;
    private final PersistentScriptVersionRepository scriptVersionRepository;
    private final PersistentTaskPlanRepository planRepository;

    public DashboardController(
            ProjectOperations projectService,
            PersistentScriptVersionRepository scriptVersionRepository,
            PersistentTaskPlanRepository planRepository
    ) {
        this.projectService = projectService;
        this.scriptVersionRepository = scriptVersionRepository;
        this.planRepository = planRepository;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        List<Project> projects = projectService.listProjects(true);
        long activeProjectCount = projects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.ACTIVE)
                .count();
        long archivedProjectCount = projects.size() - activeProjectCount;
        List<Project> recentProjects = projects.stream()
                .sorted(Comparator.comparingLong(Project::getId).reversed())
                .limit(4)
                .toList();
        return new DashboardSummary(
                activeProjectCount,
                archivedProjectCount,
                scriptVersionRepository.count(),
                planRepository.count(),
                recentProjects
        );
    }

    public record DashboardSummary(
            long activeProjectCount,
            long archivedProjectCount,
            long scriptAssetTotal,
            long taskTotal,
            List<Project> recentProjects
    ) {
    }
}
