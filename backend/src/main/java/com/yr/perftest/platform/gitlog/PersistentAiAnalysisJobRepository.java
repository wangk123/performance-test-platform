package com.yr.perftest.platform.gitlog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentAiAnalysisJobRepository extends JpaRepository<PersistentAiAnalysisJobRecord, Long> {
}
