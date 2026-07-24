package com.yr.perftest.platform.identity;

public sealed interface Principal permits HumanPrincipal, MachinePrincipal {
}
