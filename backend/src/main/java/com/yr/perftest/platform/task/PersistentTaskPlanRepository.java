package com.yr.perftest.platform.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersistentTaskPlanRepository extends JpaRepository<PersistentTaskPlanRecord, Long> {
    List<PersistentTaskPlanRecord> findAllByProjectIdOrderByIdDesc(Long projectId);

    long countByProjectId(Long projectId);

    /** 悲观行锁读取：锁持有至事务提交，供 updateMarkdown 的 revision CAS 串行化（设计 §5.2 409 契约）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PersistentTaskPlanRecord p where p.id = :id")
    Optional<PersistentTaskPlanRecord> findWithLockingById(@Param("id") Long id);
}
