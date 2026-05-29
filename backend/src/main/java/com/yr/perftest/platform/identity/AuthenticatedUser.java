package com.yr.perftest.platform.identity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class AuthenticatedUser {
    private final String username;
    private final String displayName;
    private final Set<SystemRole> roles;

    public AuthenticatedUser(String username, String displayName, Set<SystemRole> roles) {
        this.username = username;
        this.displayName = displayName;
        this.roles = Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<SystemRole> getRoles() {
        return roles;
    }

    public boolean hasRole(SystemRole role) {
        return roles.contains(role);
    }
}
