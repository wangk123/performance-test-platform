package com.yr.perftest.platform.identity;

import org.junit.jupiter.api.BeforeEach;
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
@Import(AuthTokenService.class)
class AuthTokenServiceTest {
    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private PersistentAuthTokenRepository authTokenRepository;

    @Autowired
    private PersistentUserAccountRepository userAccountRepository;

    @BeforeEach
    void seedUser() {
        userAccountRepository.save(new PersistentUserAccountRecord(
                "admin",
                "admin123",
                "平台管理员",
                true,
                SystemRole.ADMIN
        ));
    }

    @Test
    void issueReturnsPlainTokenAndStoresOnlyHash() {
        String plainToken = authTokenService.issue("admin");

        assertThat(plainToken).isNotBlank();
        assertThat(authTokenRepository.findAll()).hasSize(1);
        PersistentAuthTokenRecord stored = authTokenRepository.findAll().get(0);
        assertThat(stored.getTokenHash()).isEqualTo(sha256(plainToken));
        assertThat(stored.getTokenHash()).isNotEqualTo(plainToken);
        assertThat(stored.getUsername()).isEqualTo("admin");
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void resolveReturnsHumanPrincipalForValidToken() {
        String plainToken = authTokenService.issue("admin");

        Optional<HumanPrincipal> principal = authTokenService.resolve(plainToken);

        assertThat(principal).isPresent();
        assertThat(principal.get().username()).isEqualTo("admin");
        assertThat(principal.get().roles()).contains(SystemRole.ADMIN);
    }

    @Test
    void resolveFailsForExpiredToken() {
        String plainToken = authTokenService.issue("admin");
        PersistentAuthTokenRecord stored = authTokenRepository.findAll().get(0);
        stored.expireAt(Instant.now().minusSeconds(1));
        authTokenRepository.save(stored);

        assertThat(authTokenService.resolve(plainToken)).isEmpty();
    }

    @Test
    void revokeMakesTokenUnresolvable() {
        String plainToken = authTokenService.issue("admin");
        authTokenService.revoke(plainToken);

        assertThat(authTokenService.resolve(plainToken)).isEmpty();
        assertThat(authTokenRepository.findAll()).isEmpty();
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
