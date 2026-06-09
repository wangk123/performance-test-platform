package com.yr.perftest.platform.api;

import com.yr.perftest.platform.project.Project;
import com.yr.perftest.platform.project.ProjectMemberInfo;
import com.yr.perftest.platform.project.ProjectOperations;
import com.yr.perftest.platform.project.ProjectRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectOperations projectService;

    public ProjectController(ProjectOperations projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<Project> listProjects(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return projectService.listProjects(includeArchived);
    }

    @GetMapping("/{projectId}")
    public Project getProject(@PathVariable long projectId) {
        return projectService.getProject(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return projectService.createProject(
                request.code(),
                request.name(),
                request.description(),
                operatorUsername
        );
    }

    @PutMapping("/{projectId}")
    public Project updateProject(
            @PathVariable long projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        return projectService.updateProject(
                projectId,
                request.name(),
                request.description(),
                request.ownerUsername(),
                operatorUsername
        );
    }

    @PatchMapping("/{projectId}/archive")
    public Project archiveProject(
            @PathVariable long projectId,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        projectService.archiveProject(projectId, operatorUsername);
        return projectService.listProjects(true).stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .orElseThrow();
    }

    @PatchMapping("/{projectId}/restore")
    public Project restoreProject(
            @PathVariable long projectId,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        projectService.restoreProject(projectId, operatorUsername);
        return projectService.listProjects(true).stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .orElseThrow();
    }

    @GetMapping("/{projectId}/members")
    public List<ProjectMemberInfo> listMembers(@PathVariable long projectId) {
        return projectService.listMembers(projectId);
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberInfo addMember(
            @PathVariable long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        projectService.addMember(projectId, request.username(), request.role(), operatorUsername);
        return projectService.listMembers(projectId).stream()
                .filter(member -> member.username().equals(request.username()))
                .findFirst()
                .orElseThrow();
    }

    @DeleteMapping("/{projectId}/members/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable long projectId,
            @PathVariable String username,
            @RequestHeader(name = "X-User", defaultValue = "admin") String operatorUsername
    ) {
        projectService.removeMember(projectId, username, operatorUsername);
    }

    public record CreateProjectRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank String name,
            String description,
            @NotBlank String ownerUsername
    ) {
    }

    public record AddProjectMemberRequest(
            @NotBlank String username,
            @NotNull
            ProjectRole role
    ) {
    }
}
