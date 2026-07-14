package com.yr.perftest.platform.llm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentModelProviderRepository extends JpaRepository<PersistentModelProviderRecord, Long> {
    List<PersistentModelProviderRecord> findAllByOrderByIdDesc();

    Optional<PersistentModelProviderRecord> findByName(String name);

    List<PersistentModelProviderRecord> findAllByEnabledTrueOrderByIdAsc();
}
