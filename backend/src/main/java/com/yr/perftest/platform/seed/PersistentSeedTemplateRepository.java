package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedTemplateRepository extends JpaRepository<PersistentSeedTemplateRecord, Long> {
    List<PersistentSeedTemplateRecord> findByProjectIdOrderByIdDesc(long projectId);

    Optional<PersistentSeedTemplateRecord> findByIdAndProjectId(long id, long projectId);
}
