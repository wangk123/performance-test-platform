package com.yr.perftest.platform.identity;

public record MachinePrincipal(long apiKeyId, String scope) implements Principal {
}
