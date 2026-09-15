package com.yr.perftest.platform.envcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "env_check_credential")
public class PersistentEnvCheckCredentialRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    /** 空=项目级池；非空=该计划覆盖（查询时计划覆盖优先于项目池）。 */
    private Long planId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int sshPort;

    @Column(nullable = false, length = 128)
    private String username;

    /** AES-GCM 密文（密码或密钥内容），只写不回显。 */
    @Column(nullable = false, length = 4096)
    private String secretCipher;

    /** PASSWORD / KEY */
    @Column(nullable = false, length = 20)
    private String authType;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false, length = 64)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PersistentEnvCheckCredentialRecord() {
    }

    public PersistentEnvCheckCredentialRecord(Long projectId, Long planId, String host, int sshPort,
                                              String username, String secretCipher, String authType,
                                              String remark, String createdBy) {
        this.projectId = projectId;
        this.planId = planId;
        this.host = host;
        this.sshPort = sshPort;
        this.username = username;
        this.secretCipher = secretCipher;
        this.authType = authType;
        this.remark = remark;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getHost() {
        return host;
    }

    public int getSshPort() {
        return sshPort;
    }

    public String getUsername() {
        return username;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public String getAuthType() {
        return authType;
    }

    public String getRemark() {
        return remark;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(int sshPort, String username, String secretCipher, String authType, String remark) {
        this.sshPort = sshPort;
        this.username = username;
        this.secretCipher = secretCipher;
        this.authType = authType;
        this.remark = remark;
        this.updatedAt = Instant.now();
    }
}
