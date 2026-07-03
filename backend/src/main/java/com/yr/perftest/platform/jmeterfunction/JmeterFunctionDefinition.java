package com.yr.perftest.platform.jmeterfunction;

import java.util.List;

public record JmeterFunctionDefinition(
        String key,
        String displayName,
        String category,
        String description,
        List<JmeterFunctionParameter> parameters,
        String example
) {
}
