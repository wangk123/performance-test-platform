package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.project.Project;
import com.yr.perftest.platform.project.ProjectOperations;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目查询 agent 面入口（T12 用）：仅查询类能力，写操作维持 UI 面既有接口。
 */
@Service
public class ProjectFacade {
    private final FacadeGuard guard;
    private final ProjectOperations projectOperations;

    public ProjectFacade(FacadeGuard guard, ProjectOperations projectOperations) {
        this.guard = guard;
        this.projectOperations = projectOperations;
    }

    public List<Project> listProjects(boolean includeArchived) {
        return guard.requirePrincipal(() -> projectOperations.listProjects(includeArchived));
    }
}
