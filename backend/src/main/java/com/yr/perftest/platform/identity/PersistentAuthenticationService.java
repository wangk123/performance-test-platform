package com.yr.perftest.platform.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentAuthenticationService {
    private final PersistentUserAccountRepository userAccountRepository;

    public PersistentAuthenticationService(PersistentUserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String username, String password) {
        PersistentUserAccountRecord account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("invalid username or password"));
        if (!account.passwordMatches(password)) {
            throw new AuthenticationException("invalid username or password");
        }
        if (!account.isEnabled()) {
            throw new AuthenticationException("user is disabled");
        }
        return account.toAuthenticatedUser();
    }
}
