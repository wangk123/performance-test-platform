package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentPlanVersionRepository extends JpaRepository<PersistentPlanVersionRecord, Long> {
    /** 发布时间倒序（版本号为手输字符串，不做字典序排序）；同刻以 id 兜底稳定序。 */
    List<PersistentPlanVersionRecord> findByPlanIdOrderByCreatedAtDescIdDesc(Long planId);

    boolean existsByPlanIdAndVersionNo(Long planId, String versionNo);
}
