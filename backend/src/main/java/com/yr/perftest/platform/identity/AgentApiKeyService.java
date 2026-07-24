package com.yr.perftest.platform.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class AgentApiKeyService {
    private static final String KEY_PREFIX = "pak_";

    private final PersistentAgentApiKeyRepository agentApiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentApiKeyService(PersistentAgentApiKeyRepository agentApiKeyRepository) {
        this.agentApiKeyRepository = agentApiKeyRepository;
    }

    @Transactional
    public IssuedApiKey issue(String scope, Instant expiresAt) {
        String plainKey = KEY_PREFIX + generateSecret();
        Instant now = Instant.now();
        PersistentAgentApiKeyRecord saved = agentApiKeyRepository.save(new PersistentAgentApiKeyRecord(
                sha256(plainKey),
                plainKey.substring(0, Math.min(12, plainKey.length())),
                scope,
                expiresAt,
                now
        ));
        return new IssuedApiKey(saved.getId(), plainKey, saved.getPrefix(), saved.getScope(), saved.getExpiresAt(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<MachinePrincipal> resolve(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return Optional.empty();
        }
        return agentApiKeyRepository.findByKeyHash(sha256(plainKey))
                .filter(this::isUsable)
                .map(record -> new MachinePrincipal(record.getId(), record.getScope()));
    }

    @Transactional
    public void revoke(long id) {
        agentApiKeyRepository.findById(id).ifPresent(record -> {
            record.revokeAt(Instant.now());
            agentApiKeyRepository.save(record);
        });
    }

    @Transactional(readOnly = true)
    public List<AgentApiKeyView> list() {
        return agentApiKeyRepository.findAll().stream()
                .map(record -> new AgentApiKeyView(
                        record.getId(),
                        record.getPrefix(),
                        record.getScope(),
                        record.getExpiresAt(),
                        record.getRevokedAt(),
                        record.getCreatedAt(),
                        statusOf(record)
                ))
                .toList();
    }

    private boolean isUsable(PersistentAgentApiKeyRecord record) {
        if (record.getRevokedAt() != null) {
            return false;
        }
        return record.getExpiresAt() == null || record.getExpiresAt().isAfter(Instant.now());
    }

    private String statusOf(PersistentAgentApiKeyRecord record) {
        if (record.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (record.getExpiresAt() != null && !record.getExpiresAt().isAfter(Instant.now())) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private String generateSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record IssuedApiKey(
            long id,
            String plainKey,
            String prefix,
            String scope,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    public record AgentApiKeyView(
            long id,
            String prefix,
            String scope,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt,
            String status
    ) {
    }
}
