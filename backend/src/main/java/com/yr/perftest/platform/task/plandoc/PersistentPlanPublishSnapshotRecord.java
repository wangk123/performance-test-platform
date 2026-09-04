package com.yr.perftest.platform.task.plandoc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "plan_publish_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"planId", "revision"}))
public class PersistentPlanPublishSnapshotRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false, length = 80)
    private String publishedBy;

    @Column(nullable = false)
    private Instant publishedAt;

    @Lob
    @Column(nullable = false)
    private String docJson;

    @Lob
    @Column(nullable = false)
    private String scenarioJson;

    @Lob
    private String summaryJson;

    protected PersistentPlanPublishSnapshotRecord() {
    }

    public PersistentPlanPublishSnapshotRecord(Long planId, int revision, String publishedBy,
                                               Instant publishedAt, String docJson, String scenarioJson, String summaryJson) {
        this.planId = planId;
        this.revision = revision;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.docJson = docJson;
        this.scenarioJson = scenarioJson;
        this.summaryJson = summaryJson;
    }

    public Long getId() { return id; }
    public Long getPlanId() { return planId; }
    public int getRevision() { return revision; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getDocJson() { return docJson; }
    public String getScenarioJson() { return scenarioJson; }
    public String getSummaryJson() { return summaryJson; }
}
