package com.yr.perftest.platform.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "seed_template")
public class PersistentSeedTemplateRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long captureSessionId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Integer versionNo;

    @Lob
    @Column(nullable = false)
    private String bodyJson;

    @Lob
    private String seedRowsJson;

    private String confirmedBy;

    private Instant confirmedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentSeedTemplateRecord() {
    }

    public PersistentSeedTemplateRecord(long projectId, long captureSessionId, String bodyJson, String seedRowsJson) {
        Instant now = Instant.now();
        this.projectId = projectId;
        this.captureSessionId = captureSessionId;
        this.status = "DRAFT";
        this.versionNo = 1;
        this.bodyJson = bodyJson;
        this.seedRowsJson = seedRowsJson;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDraft(String bodyJson) {
        if (!"DRAFT".equals(status)) {
            throw new SeedValidationException("confirmed template version is immutable");
        }
        this.bodyJson = bodyJson;
        this.updatedAt = Instant.now();
    }

    public void confirm(String operator) {
        TemplateConfirmValidator.validate(SeedJson.draftFromJson(bodyJson));
        this.status = "CONFIRMED";
        this.confirmedBy = operator;
        this.confirmedAt = Instant.now();
        this.updatedAt = this.confirmedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getCaptureSessionId() {
        return captureSessionId;
    }

    public String getStatus() {
        return status;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getBodyJson() {
        return bodyJson;
    }

    public String getSeedRowsJson() {
        return seedRowsJson;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
