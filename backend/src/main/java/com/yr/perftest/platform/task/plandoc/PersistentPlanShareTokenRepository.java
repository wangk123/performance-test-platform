package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentPlanShareTokenRepository extends JpaRepository<PersistentPlanShareTokenRecord, Long> {
    Optional<PersistentPlanShareTokenRecord> findByToken(String token);

    List<PersistentPlanShareTokenRecord> findAllByPlanIdOrderByIdDesc(Long planId);

    void deleteAllByPlanId(Long planId);
}
