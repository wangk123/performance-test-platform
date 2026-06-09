package com.yr.perftest.platform.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentProjectMemberRepository extends JpaRepository<PersistentProjectMemberRecord, Long> {
    boolean existsByProjectIdAndUsername(Long projectId, String username);

    boolean existsByProjectIdAndUsernameAndRole(Long projectId, String username, ProjectRole role);

    List<PersistentProjectMemberRecord> findAllByProjectIdOrderByIdAsc(Long projectId);

    Optional<PersistentProjectMemberRecord> findByProjectIdAndUsername(Long projectId, String username);
}
