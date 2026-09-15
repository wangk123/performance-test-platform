package com.yr.perftest.platform.envcheck;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** 凭据池：项目级 + 计划覆盖；密码只写不回显（CredentialView 不含任何密文字段）。 */
@Service
public class EnvCheckCredentialService {

    public record CredentialView(long id, String host, int sshPort, String username, String authType,
                                 String remark, Long planId) {
    }

    public record CredentialInput(String host, Integer sshPort, String username, String password,
                                  String keyMaterial, String remark, Long planId) {
    }

    public record ConnectionTest(boolean ok, String message) {
    }

    /** 仅服务内部使用，任何 API 响应不得返回。 */
    public record ResolvedCredential(String host, int sshPort, String username, String password, String keyMaterial) {
    }

    private final PersistentEnvCheckCredentialRepository repository;
    private final EnvCheckCredentialCipher cipher;
    private final EnvProbeClient probeClient;

    public EnvCheckCredentialService(PersistentEnvCheckCredentialRepository repository,
                                     EnvCheckProperties properties,
                                     EnvProbeClient probeClient) {
        this.repository = repository;
        this.cipher = new EnvCheckCredentialCipher(properties.getSecret());
        this.probeClient = probeClient;
    }

    public List<CredentialView> list(long projectId) {
        return repository.findByProjectIdOrderByHostAsc(projectId).stream().map(this::toView).toList();
    }

    public CredentialView save(long projectId, String actor, CredentialInput in) {
        if (in.host() == null || in.host().isBlank()) {
            throw new EnvCheckValidationException("ENV_CREDENTIAL_INVALID：host 不能为空");
        }
        if (in.username() == null || in.username().isBlank()) {
            throw new EnvCheckValidationException("ENV_CREDENTIAL_INVALID：username 不能为空");
        }
        boolean key = in.keyMaterial() != null && !in.keyMaterial().isBlank();
        String authType = key ? "KEY" : "PASSWORD";
        String plain = key ? in.keyMaterial() : in.password();
        int sshPort = in.sshPort() == null ? 22 : in.sshPort();
        String secretCipher = cipher.encrypt(plain);
        PersistentEnvCheckCredentialRecord record = findByProjectPlanHost(projectId, in.planId(), in.host())
                .map(existing -> {
                    existing.update(sshPort, in.username(), secretCipher, authType, in.remark());
                    return existing;
                })
                .orElseGet(() -> new PersistentEnvCheckCredentialRecord(
                        projectId, in.planId(), in.host(), sshPort, in.username(), secretCipher, authType, in.remark(), actor));
        return toView(repository.save(record));
    }

    public void delete(long projectId, long id) {
        PersistentEnvCheckCredentialRecord record = owned(projectId, id);
        repository.delete(record);
    }

    public ConnectionTest test(long projectId, long id) {
        PersistentEnvCheckCredentialRecord record = owned(projectId, id);
        return probeClient.testConnection(toResolved(record));
    }

    /** 计划覆盖 &gt; 项目池：planId 非空时先查计划级，未命中回落项目池。 */
    public Optional<ResolvedCredential> resolve(long projectId, Long planId, String host) {
        Optional<PersistentEnvCheckCredentialRecord> record = planId == null
                ? repository.findByProjectIdAndPlanIdIsNullAndHost(projectId, host)
                : repository.findByProjectIdAndPlanIdAndHost(projectId, planId, host)
                        .or(() -> repository.findByProjectIdAndPlanIdIsNullAndHost(projectId, host));
        return record.map(this::toResolved);
    }

    private Optional<PersistentEnvCheckCredentialRecord> findByProjectPlanHost(long projectId, Long planId, String host) {
        return planId == null
                ? repository.findByProjectIdAndPlanIdIsNullAndHost(projectId, host)
                : repository.findByProjectIdAndPlanIdAndHost(projectId, planId, host);
    }

    private PersistentEnvCheckCredentialRecord owned(long projectId, long id) {
        return repository.findById(id)
                .filter(record -> record.getProjectId() != null && record.getProjectId() == projectId)
                .orElseThrow(() -> new EnvCheckValidationException("ENV_CREDENTIAL_INVALID：凭据不存在"));
    }

    private ResolvedCredential toResolved(PersistentEnvCheckCredentialRecord record) {
        String secret = cipher.decrypt(record.getSecretCipher());
        boolean key = "KEY".equals(record.getAuthType());
        return new ResolvedCredential(record.getHost(), record.getSshPort(), record.getUsername(),
                key ? null : secret, key ? secret : null);
    }

    private CredentialView toView(PersistentEnvCheckCredentialRecord record) {
        return new CredentialView(record.getId(), record.getHost(), record.getSshPort(), record.getUsername(),
                record.getAuthType(), record.getRemark(), record.getPlanId());
    }
}
