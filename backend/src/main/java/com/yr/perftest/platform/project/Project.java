package com.yr.perftest.platform.project;

import java.util.Objects;

public class Project {
    private final long id;
    private final String code;
    private final String name;
    private final String description;
    private final String ownerUsername;
    private ProjectStatus status;

    Project(long id, String code, String name, String description, String ownerUsername) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.ownerUsername = ownerUsername;
        this.status = ProjectStatus.ACTIVE;
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    void archive() {
        status = ProjectStatus.ARCHIVED;
    }

    void restore() {
        status = ProjectStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Project)) {
            return false;
        }
        Project project = (Project) other;
        return id == project.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
