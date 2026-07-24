package com.yr.perftest.platform.identity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record HumanPrincipal(String username, Set<SystemRole> roles) implements Principal {
    public HumanPrincipal {
        roles = Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
