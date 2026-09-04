package com.yr.perftest.platform.task.plandoc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersistentPlanTemplateRepository extends JpaRepository<PersistentPlanTemplateRecord, Long> {
    @Query("select t from PersistentPlanTemplateRecord t where t.projectId is null or t.projectId = :projectId order by t.builtin desc, t.id asc")
    List<PersistentPlanTemplateRecord> findAllVisible(@Param("projectId") Long projectId);

    Optional<PersistentPlanTemplateRecord> findFirstByBuiltinTrueOrderByIdAsc();
}
