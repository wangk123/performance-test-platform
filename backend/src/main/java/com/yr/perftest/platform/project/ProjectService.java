package com.yr.perftest.platform.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectService {
    private long nextProjectId = 1L;
    private final Map<Long, Project> projectsById = new LinkedHashMap<Long, Project>();
    private final Map<String, Project> projectsByCode = new LinkedHashMap<String, Project>();
    private final List<ProjectMember> members = new ArrayList<ProjectMember>();

    public Project createProject(String code, String name, String description, String ownerUsername) {
        if (isBlank(code)) {
            throw new ProjectValidationException("project code is required");
        }
        if (isBlank(name)) {
            throw new ProjectValidationException("project name is required");
        }
        if (projectsByCode.containsKey(code)) {
            throw new ProjectValidationException("project code already exists");
        }

        Project project = new Project(nextProjectId++, code, name, description, ownerUsername);
        projectsById.put(project.getId(), project);
        projectsByCode.put(project.getCode(), project);
        members.add(new ProjectMember(project.getId(), ownerUsername, ProjectRole.OWNER));
        return project;
    }

    public void archiveProject(long projectId, String operatorUsername) {
        Project project = requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        project.archive();
    }

    public void restoreProject(long projectId, String operatorUsername) {
        Project project = requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        project.restore();
    }

    public void addMember(long projectId, String username, ProjectRole role, String operatorUsername) {
        requireProject(projectId);
        requireProjectOwner(projectId, operatorUsername);
        if (!canAccessProject(projectId, username)) {
            members.add(new ProjectMember(projectId, username, role));
        }
    }

    public boolean canAccessProject(long projectId, String username) {
        for (ProjectMember member : members) {
            if (member.getProjectId() == projectId && member.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public List<Project> listProjectsAvailableForTaskSelection() {
        return listProjects(false);
    }

    public List<Project> listProjects(boolean includeArchived) {
        List<Project> projects = new ArrayList<Project>();
        for (Project project : projectsById.values()) {
            if (includeArchived || project.getStatus() == ProjectStatus.ACTIVE) {
                projects.add(project);
            }
        }
        return projects;
    }

    private Project requireProject(long projectId) {
        Project project = projectsById.get(projectId);
        if (project == null) {
            throw new ProjectValidationException("project does not exist");
        }
        return project;
    }

    private void requireProjectOwner(long projectId, String username) {
        for (ProjectMember member : members) {
            if (member.getProjectId() == projectId && member.getUsername().equals(username) && member.getRole() == ProjectRole.OWNER) {
                return;
            }
        }
        throw new ProjectValidationException("project owner permission is required");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
