package com.yr.perftest.platform.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 变更登记（T9）：代码/配置引用登记入口，优化验证必须挂靠已登记的变更。
 */
@Entity
@Table(name = "change_records")
public class PersistentChangeRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChangeType changeType;

    @Column(nullable = false, length = 128)
    private String changeRef;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 16)
    private String registeredByType;

    @Column(nullable = false, length = 128)
    private String registeredByName;

    @Column(nullable = false)
    private Instant createdAt;

    protected PersistentChangeRecord() {
    }

    public PersistentChangeRecord(
            ChangeType changeType,
            String changeRef,
            String description,
            String registeredByType,
            String registeredByName
    ) {
        this.changeType = changeType;
        this.changeRef = changeRef;
        this.description = description;
        this.registeredByType = registeredByType;
        this.registeredByName = registeredByName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getChangeRef() {
        return changeRef;
    }

    public String getDescription() {
        return description;
    }

    public String getRegisteredByType() {
        return registeredByType;
    }

    public String getRegisteredByName() {
        return registeredByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
