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

    /**
     * Convert the string type to the strongly-typed {@link ScriptStepType} enum.
     *
     * @return the matching enum value, or {@code null} if the type is unknown
     */
    public ScriptStepType stepType() {
        return ScriptStepType.fromCode(type);
    }

    /**
     * Get the thread group configuration from this step's config map.
     *
     * @return the parsed ThreadGroupConfig
     * @throws IllegalStateException if this step is not a THREAD_GROUP
     */
    public ThreadGroupConfig threadGroupConfig() {
        if (!ScriptStepType.THREAD_GROUP.code().equals(type)) {
            throw new IllegalStateException("Step type '" + type + "' is not a THREAD_GROUP");
        }
        return ThreadGroupConfig.fromMap(config);
    }
}
