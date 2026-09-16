package com.yr.perftest.platform.datafile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataFileRepository extends JpaRepository<PersistentDataFileRecord, Long> {
    Optional<PersistentDataFileRecord> findByProjectIdAndName(long projectId, String name);

    List<PersistentDataFileRecord> findAllByProjectIdOrderByIdAsc(long projectId);
}
