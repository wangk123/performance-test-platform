package com.yr.perftest.platform.execution;

import java.util.Map;

public record ExecutionConfig(
        int threads,
        int rampUp,
        int duration,
        int loops,
        String environment,
        Map<String, String> jmeterProperties
) {
    public ExecutionConfig {
        jmeterProperties = jmeterProperties == null ? Map.of() : Map.copyOf(jmeterProperties);
    }
}
