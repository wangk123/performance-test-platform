package com.yr.perftest.platform.monitoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PersistentMonitorTargetRepository extends JpaRepository<PersistentMonitorTargetRecord, Long> {
    List<PersistentMonitorTargetRecord> findAllByProjectIdOrderByIdDesc(Long projectId);

    List<PersistentMonitorTargetRecord> findAllByEnabledTrueOrderByIdAsc();

    List<PersistentMonitorTargetRecord> findAllByIdIn(Collection<Long> ids);
}
