package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 凭据加密 roundtrip 与保存规则（新建空密钥拒绝、编辑空密码保留旧密文；resolve 计划覆盖优先属 Task 8 集成断言）。 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:envcheck-credential-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class EnvCheckCredentialServiceTest {

    @Autowired
    PersistentEnvCheckCredentialRepository credentials;

    private EnvCheckCredentialService service;
    private EnvCheckCredentialCipher cipher;

    @BeforeEach
    void setUp() {
        EnvCheckProperties properties = new EnvCheckProperties();
        properties.setSecret("unit-test-secret");
        cipher = new EnvCheckCredentialCipher("unit-test-secret");
        service = new EnvCheckCredentialService(credentials, properties, new EnvProbeClient());
    }

    @Test
    void cipherRoundtripAndPrefix() {
        String enc = cipher.encrypt("p@ss");
        assertThat(enc).startsWith("enc:v1:").doesNotContain("p@ss");
        assertThat(cipher.decrypt(enc)).isEqualTo("p@ss");
    }

    @Test
    void cipherIsIdempotentOnCiphertext() {
        String enc = cipher.encrypt("x");
        assertThat(cipher.encrypt(enc)).isEqualTo(enc);
    }

    @Test
    void saveRejectsBlankSecretOnCreate() {
        assertThatThrownBy(() -> service.save(1L, "alice",
                new EnvCheckCredentialService.CredentialInput("10.1.1.5", 22, "deploy", "", null, null, null)))
                .isInstanceOf(EnvCheckValidationException.class);
        assertThat(credentials.findByProjectIdOrderByHostAsc(1L)).isEmpty();
    }

    @Test
    void updateWithBlankPasswordKeepsExistingCipher() {
        service.save(2L, "alice",
                new EnvCheckCredentialService.CredentialInput("10.1.1.6", 22, "deploy", "p@ss", null, "old", null));
        String cipherBefore = credentials.findByProjectIdAndPlanIdIsNullAndHost(2L, "10.1.1.6")
                .orElseThrow().getSecretCipher();
        service.save(2L, "alice",
                new EnvCheckCredentialService.CredentialInput("10.1.1.6", 22, "deploy", null, null, "只改备注", null));
        PersistentEnvCheckCredentialRecord record = credentials.findByProjectIdAndPlanIdIsNullAndHost(2L, "10.1.1.6")
                .orElseThrow();
        assertThat(record.getSecretCipher()).isEqualTo(cipherBefore);
        assertThat(record.getAuthType()).isEqualTo("PASSWORD");
        assertThat(cipher.decrypt(record.getSecretCipher())).isEqualTo("p@ss");
        assertThat(record.getRemark()).isEqualTo("只改备注");
    }

    @Test
    void updateWithNewPasswordReplacesCipher() {
        service.save(3L, "alice",
                new EnvCheckCredentialService.CredentialInput("10.1.1.7", 22, "deploy", "old-pass", null, null, null));
        service.save(3L, "alice",
                new EnvCheckCredentialService.CredentialInput("10.1.1.7", 22, "deploy", "new-pass", null, null, null));
        assertThat(credentials.findByProjectIdOrderByHostAsc(3L)).hasSize(1);
        PersistentEnvCheckCredentialRecord record = credentials.findByProjectIdAndPlanIdIsNullAndHost(3L, "10.1.1.7")
                .orElseThrow();
        assertThat(cipher.decrypt(record.getSecretCipher())).isEqualTo("new-pass");
    }
}
