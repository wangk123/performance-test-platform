package com.yr.perftest.platform.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthTokenService {
    static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final PersistentAuthTokenRepository authTokenRepository;
    private final PersistentUserAccountRepository userAccountRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(
            PersistentAuthTokenRepository authTokenRepository,
            PersistentUserAccountRepository userAccountRepository
    ) {
        this.authTokenRepository = authTokenRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public String issue(String username) {
        PersistentUserAccountRecord account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("user not found"));
        String plainToken = generateToken();
        Instant now = Instant.now();
        authTokenRepository.save(new PersistentAuthTokenRecord(
                account.getUsername(),
                sha256(plainToken),
                now.plus(DEFAULT_TTL),
                now
        ));
        return plainToken;
    }

    @Transactional(readOnly = true)
    public Optional<HumanPrincipal> resolve(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findByTokenHash(sha256(plainToken))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .flatMap(token -> userAccountRepository.findByUsername(token.getUsername()))
                .filter(PersistentUserAccountRecord::isEnabled)
                .map(account -> {
                    AuthenticatedUser user = account.toAuthenticatedUser();
                    return new HumanPrincipal(user.getUsername(), user.getRoles());
                });
    }

    @Transactional
    public void revoke(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return;
        }
        authTokenRepository.findByTokenHash(sha256(plainToken))
                .ifPresent(authTokenRepository::delete);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
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
}
