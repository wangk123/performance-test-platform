package com.yr.perftest.platform.project;

import java.util.List;

public interface ProjectOperations {
    Project createProject(String code, String name, String description, String ownerUsername);

    Project getProject(long projectId);

    Project updateProject(long projectId, String name, String description, String ownerUsername, String operatorUsername);

    void archiveProject(long projectId, String operatorUsername);

    void restoreProject(long projectId, String operatorUsername);

    void addMember(long projectId, String username, ProjectRole role, String operatorUsername);

    void removeMember(long projectId, String username, String operatorUsername);

    boolean canAccessProject(long projectId, String username);

    List<ProjectMemberInfo> listMembers(long projectId);

    List<Project> listProjectsAvailableForTaskSelection();

    List<Project> listProjects(boolean includeArchived);
}
