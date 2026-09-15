package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 凭据池：加密 roundtrip + resolve 计划覆盖优先 + 密文不出明文。 */
class EnvCheckCredentialServiceTest {

    @Test
    void cipherRoundtripAndPrefix() {
        EnvCheckCredentialCipher cipher = new EnvCheckCredentialCipher("unit-test-secret");
        String enc = cipher.encrypt("p@ss");
        assertThat(enc).startsWith("enc:v1:").doesNotContain("p@ss");
        assertThat(cipher.decrypt(enc)).isEqualTo("p@ss");
    }

    @Test
    void cipherIsIdempotentOnCiphertext() {
        EnvCheckCredentialCipher cipher = new EnvCheckCredentialCipher("k");
        String enc = cipher.encrypt("x");
        assertThat(cipher.encrypt(enc)).isEqualTo(enc);
    }
}
