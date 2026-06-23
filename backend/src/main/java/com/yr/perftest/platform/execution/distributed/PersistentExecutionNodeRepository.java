package com.yr.perftest.platform.execution.distributed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersistentExecutionNodeRepository extends JpaRepository<PersistentExecutionNodeRecord, Long> {
    List<PersistentExecutionNodeRecord> findAllByOrderByIdDesc();

    Optional<PersistentExecutionNodeRecord> findByHostAndSshUsername(String host, String sshUsername);
}
