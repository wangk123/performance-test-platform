package com.yr.perftest.platform.project;

class ProjectMember {
    private final long projectId;
    private final String username;
    private final ProjectRole role;

    ProjectMember(long projectId, String username, ProjectRole role) {
        this.projectId = projectId;
        this.username = username;
        this.role = role;
    }

    long getProjectId() {
        return projectId;
    }

    String getUsername() {
        return username;
    }

    ProjectRole getRole() {
        return role;
    }
}
