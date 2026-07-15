package com.yr.perftest.platform.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedCredentialCipherTest {
    @Test
    void roundTripEncryptDecrypt() {
        SeedCredentialCipher cipher = new SeedCredentialCipher("unit-test-secret-key");
        String encrypted = cipher.encrypt("s3cret");
        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("s3cret");
    }
}
