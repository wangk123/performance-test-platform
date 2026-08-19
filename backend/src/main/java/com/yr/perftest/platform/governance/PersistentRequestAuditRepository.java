package com.yr.perftest.platform.governance;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistentRequestAuditRepository extends JpaRepository<PersistentRequestAuditRecord, Long> {
}
