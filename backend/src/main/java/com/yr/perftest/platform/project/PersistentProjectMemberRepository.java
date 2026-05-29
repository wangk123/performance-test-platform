package com.yr.perftest.platform.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentProjectMemberRepository extends JpaRepository<PersistentProjectMemberRecord, Long> {
    boolean existsByProjectIdAndUsername(Long projectId, String username);

    boolean existsByProjectIdAndUsernameAndRole(Long projectId, String username, ProjectRole role);
}
