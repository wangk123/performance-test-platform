package com.yr.perftest.platform.script;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "script_versions")
public class PersistentScriptVersionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Integer versionNo;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 1000)
    private String storedPath;

    @Column(nullable = false, length = 80)
    private String uploadedBy;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected PersistentScriptVersionRecord() {
    }

    PersistentScriptVersionRecord(
            Long projectId,
            Integer versionNo,
            String originalFilename,
            String storedPath,
            String uploadedBy,
            Instant uploadedAt
    ) {
        this.projectId = projectId;
        this.versionNo = versionNo;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    ScriptVersion toScriptVersion() {
        return new ScriptVersion(
                id,
                projectId,
                versionNo,
                originalFilename,
                storedPath,
                uploadedBy,
                uploadedAt
        );
    }
}
