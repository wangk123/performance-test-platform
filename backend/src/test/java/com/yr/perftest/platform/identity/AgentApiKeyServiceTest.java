package com.yr.perftest.platform.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AgentApiKeyService.class)
class AgentApiKeyServiceTest {
    @Autowired
    private AgentApiKeyService agentApiKeyService;

    @Autowired
    private PersistentAgentApiKeyRepository agentApiKeyRepository;

    @Test
    void issueReturnsPrefixedPlainKeyAndStoresOnlyHash() {
        AgentApiKeyService.IssuedApiKey issued = agentApiKeyService.issue(null, null);

        assertThat(issued.plainKey()).startsWith("pak_");
        assertThat(agentApiKeyRepository.findAll()).hasSize(1);
        PersistentAgentApiKeyRecord stored = agentApiKeyRepository.findAll().get(0);
        assertThat(stored.getKeyHash()).isEqualTo(sha256(issued.plainKey()));
        assertThat(stored.getKeyHash()).isNotEqualTo(issued.plainKey());
        assertThat(stored.getPrefix()).isEqualTo(issued.plainKey().substring(0, Math.min(12, issued.plainKey().length())));
        assertThat(issued.id()).isEqualTo(stored.getId());
    }

    @Test
    void resolveReturnsMachinePrincipalForValidKey() {
        AgentApiKeyService.IssuedApiKey issued = agentApiKeyService.issue("ops", null);

        Optional<MachinePrincipal> principal = agentApiKeyService.resolve(issued.plainKey());

        assertThat(principal).isPresent();
        assertThat(principal.get().apiKeyId()).isEqualTo(issued.id());
        assertThat(principal.get().scope()).isEqualTo("ops");
    }

    @Test
    void resolveFailsAfterRevoke() {
        AgentApiKeyService.IssuedApiKey issued = agentApiKeyService.issue(null, null);
        agentApiKeyService.revoke(issued.id());

        assertThat(agentApiKeyService.resolve(issued.plainKey())).isEmpty();
        PersistentAgentApiKeyRecord stored = agentApiKeyRepository.findById(issued.id()).orElseThrow();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void resolveFailsForExpiredKey() {
        AgentApiKeyService.IssuedApiKey issued = agentApiKeyService.issue(null, Instant.now().minusSeconds(1));

        assertThat(agentApiKeyService.resolve(issued.plainKey())).isEmpty();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
