package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanCommentRepository extends JpaRepository<PersistentPlanCommentRecord, Long> {
    List<PersistentPlanCommentRecord> findAllByPlanIdOrderByIdAsc(Long planId);

    void deleteAllByPlanId(Long planId);
}
