package com.yr.perftest.platform.jmeterfunction;

import java.util.List;

public record JmeterFunctionParameter(
        String name,
        String description,
        boolean required
) {
}
