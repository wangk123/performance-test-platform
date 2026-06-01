package com.yr.perftest.platform.project;

public record ProjectMemberInfo(
        long projectId,
        String username,
        ProjectRole role
) {
}
