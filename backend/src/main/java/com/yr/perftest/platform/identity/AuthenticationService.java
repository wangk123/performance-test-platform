package com.yr.perftest.platform.identity;

import java.util.LinkedHashMap;
import java.util.Map;

public class AuthenticationService {
    private final Map<String, UserAccount> accountsByUsername = new LinkedHashMap<String, UserAccount>();

    public void register(String username, String password, String displayName, boolean enabled, SystemRole role) {
        if (isBlank(username)) {
            throw new AuthenticationException("username is required");
        }
        if (isBlank(password)) {
            throw new AuthenticationException("password is required");
        }
        accountsByUsername.put(username, new UserAccount(username, password, displayName, enabled, role));
    }

    public AuthenticatedUser authenticate(String username, String password) {
        UserAccount account = accountsByUsername.get(username);
        if (account == null || !account.passwordMatches(password)) {
            throw new AuthenticationException("invalid username or password");
        }
        if (!account.isEnabled()) {
            throw new AuthenticationException("user is disabled");
        }
        return account.toAuthenticatedUser();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
