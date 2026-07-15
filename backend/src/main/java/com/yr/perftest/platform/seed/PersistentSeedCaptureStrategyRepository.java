package com.yr.perftest.platform.seed;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersistentSeedCaptureStrategyRepository
        extends JpaRepository<PersistentSeedCaptureStrategyRecord, Long> {
    List<PersistentSeedCaptureStrategyRecord> findByProjectIdOrderByUpdatedAtDesc(long projectId);

    Optional<PersistentSeedCaptureStrategyRecord> findByIdAndProjectId(long id, long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select strategy
            from PersistentSeedCaptureStrategyRecord strategy
            where strategy.id = :id
              and strategy.projectId = :projectId
            """)
    Optional<PersistentSeedCaptureStrategyRecord> findByIdAndProjectIdForUpdate(
            @Param("id") long id,
            @Param("projectId") long projectId
    );
}
