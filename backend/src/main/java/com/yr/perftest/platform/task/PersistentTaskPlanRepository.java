package com.yr.perftest.platform.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentTaskPlanRepository extends JpaRepository<PersistentTaskPlanRecord, Long> {
    List<PersistentTaskPlanRecord> findAllByProjectIdOrderByIdDesc(Long projectId);

    long countByProjectId(Long projectId);
}
