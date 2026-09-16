package com.yr.perftest.platform.datafile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataFileVersionRepository extends JpaRepository<PersistentDataFileVersionRecord, Long> {
    List<PersistentDataFileVersionRecord> findAllByDataFileIdOrderByVersionNoDesc(long dataFileId);

    Optional<PersistentDataFileVersionRecord> findByDataFileIdAndVersionNo(long dataFileId, int versionNo);

    List<PersistentDataFileVersionRecord> findAllByOriginalFilename(String originalFilename);
}
