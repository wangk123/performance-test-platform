package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCloneJobRepository extends JpaRepository<PersistentSeedCloneJobRecord, Long> {
    List<PersistentSeedCloneJobRecord> findByProjectIdOrderByIdDesc(long projectId);

    Optional<PersistentSeedCloneJobRecord> findByIdAndProjectId(long id, long projectId);
}
