package com.yr.perftest.platform.identity;

import java.util.EnumSet;
import java.util.Set;

class UserAccount {
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean enabled;
    private final Set<SystemRole> roles;

    UserAccount(String username, String password, String displayName, boolean enabled, SystemRole role) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.enabled = enabled;
        this.roles = EnumSet.of(role);
    }

    String getUsername() {
        return username;
    }

    boolean passwordMatches(String rawPassword) {
        return password.equals(rawPassword);
    }

    boolean isEnabled() {
        return enabled;
    }

    AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(username, displayName, roles);
    }
}
