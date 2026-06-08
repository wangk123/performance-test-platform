package com.yr.perftest.platform.script;

import java.util.List;
import java.util.Map;

public record ScriptStepDefinition(
        String id,
        String type,
        String name,
        Map<String, Object> config,
        List<ScriptStepDefinition> children
) {
    public ScriptStepDefinition {
        config = config == null ? Map.of() : Map.copyOf(config);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
