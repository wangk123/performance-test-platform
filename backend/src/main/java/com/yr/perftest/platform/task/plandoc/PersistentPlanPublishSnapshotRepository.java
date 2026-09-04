package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanPublishSnapshotRepository extends JpaRepository<PersistentPlanPublishSnapshotRecord, Long> {
    List<PersistentPlanPublishSnapshotRecord> findAllByPlanIdOrderByRevisionDesc(Long planId);

    void deleteAllByPlanId(Long planId);
}
