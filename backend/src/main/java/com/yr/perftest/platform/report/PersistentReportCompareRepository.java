package com.yr.perftest.platform.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentReportCompareRepository extends JpaRepository<PersistentReportCompareRecord, Long> {
}
