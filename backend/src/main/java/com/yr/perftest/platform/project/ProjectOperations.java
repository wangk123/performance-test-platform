package com.yr.perftest.platform.project;

import java.util.List;

public interface ProjectOperations {
    Project createProject(String code, String name, String description, String ownerUsername);

    void archiveProject(long projectId, String operatorUsername);

    void restoreProject(long projectId, String operatorUsername);

    void addMember(long projectId, String username, ProjectRole role, String operatorUsername);

    boolean canAccessProject(long projectId, String username);

    java.util.List<ProjectMemberInfo> listMembers(long projectId);

    List<Project> listProjectsAvailableForTaskSelection();

    List<Project> listProjects(boolean includeArchived);
}
