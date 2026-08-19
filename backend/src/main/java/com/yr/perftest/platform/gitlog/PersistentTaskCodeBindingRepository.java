package com.yr.perftest.platform.gitlog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentTaskCodeBindingRepository extends JpaRepository<PersistentTaskCodeBindingRecord, Long> {
    Optional<PersistentTaskCodeBindingRecord> findByScenarioId(Long scenarioId);

    List<PersistentTaskCodeBindingRecord> findAllByRepositoryIdOrderByIdDesc(Long repositoryId);
}
