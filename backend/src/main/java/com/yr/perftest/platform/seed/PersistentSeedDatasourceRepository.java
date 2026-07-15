package com.yr.perftest.platform.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedDatasourceRepository extends JpaRepository<PersistentSeedDatasourceRecord, Long> {
    List<PersistentSeedDatasourceRecord> findByProjectIdOrderByIdDesc(long projectId);

    Optional<PersistentSeedDatasourceRecord> findByIdAndProjectId(long id, long projectId);
}
