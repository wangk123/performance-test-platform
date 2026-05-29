package com.yr.perftest.platform.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentProjectRepository extends JpaRepository<PersistentProjectRecord, Long> {
    boolean existsByCode(String code);

    Optional<PersistentProjectRecord> findByCode(String code);

    List<PersistentProjectRecord> findAllByOrderByIdAsc();

    List<PersistentProjectRecord> findAllByStatusOrderByIdAsc(ProjectStatus status);
}
