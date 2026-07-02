package com.yr.perftest.platform.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptStepType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScenarioThreadGroupConfigSupport {
    private final ObjectMapper objectMapper;
    private final JmeterScriptParser scriptParser;

    public ScenarioThreadGroupConfigSupport(ObjectMapper objectMapper, JmeterScriptParser scriptParser) {
        this.objectMapper = objectMapper;
        this.scriptParser = scriptParser;
    }

    public List<ScenarioThreadGroupConfig> readStored(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ScenarioThreadGroupConfig>>() {
            });
        } catch (Exception exception) {
            throw new ExecutionValidationException("thread group configs json is invalid");
        }
    }

    public String writeStored(List<ScenarioThreadGroupConfig> configs) {
        try {
            List<ScenarioThreadGroupConfig> stripped = configs == null
                    ? List.of()
                    : configs.stream().map(ScenarioThreadGroupConfig::withoutSummary).toList();
            return objectMapper.writeValueAsString(stripped);
        } catch (Exception exception) {
            throw new ExecutionValidationException("thread group configs json is invalid");
        }
    }

    public List<ScenarioThreadGroupConfig> normalize(Path scriptPath, List<ScenarioThreadGroupConfig> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Map<String, ScriptStepDefinition> threadGroups = loadThreadGroups(scriptPath);
        long nextId = 1;
        for (ScenarioThreadGroupConfig existing : inputs) {
            if (existing.id() >= nextId) {
                nextId = existing.id() + 1;
            }
        }
        List<ScenarioThreadGroupConfig> normalized = new ArrayList<>();
        int sortOrder = 0;
        for (ScenarioThreadGroupConfig input : inputs) {
            validateInput(input);
            ScriptStepDefinition step = threadGroups.get(input.stepId());
            if (step == null) {
                throw new ExecutionValidationException("thread group step does not exist: " + input.stepId());
            }
            long id = input.id() > 0 ? input.id() : nextId++;
            normalized.add(new ScenarioThreadGroupConfig(
                    id,
                    input.stepId(),
                    step.name(),
                    input.threads(),
                    input.rampUp(),
                    input.duration(),
                    sortOrder++,
                    null
            ));
        }
        return normalized;
    }

    public ScenarioThreadGroupConfig requireConfig(List<ScenarioThreadGroupConfig> configs, long configId) {
        return configs.stream()
                .filter(config -> config.id() == configId)
                .findFirst()
                .orElseThrow(() -> new ExecutionValidationException("thread group config does not exist"));
    }

    private Map<String, ScriptStepDefinition> loadThreadGroups(Path scriptPath) {
        try {
            String content = Files.readString(scriptPath, StandardCharsets.UTF_8);
            Map<String, ScriptStepDefinition> groups = new LinkedHashMap<>();
            collectThreadGroups(scriptParser.parseSteps(content), groups);
            return groups;
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to read script thread groups");
        }
    }

    private void collectThreadGroups(List<ScriptStepDefinition> steps, Map<String, ScriptStepDefinition> groups) {
        for (ScriptStepDefinition step : steps) {
            if (ScriptStepType.THREAD_GROUP.code().equals(step.type())) {
                groups.put(step.id(), step);
            }
            if (!step.children().isEmpty()) {
                collectThreadGroups(step.children(), groups);
            }
        }
    }

    private void validateInput(ScenarioThreadGroupConfig input) {
        if (input.stepId() == null || input.stepId().isBlank()) {
            throw new ExecutionValidationException("thread group stepId is required");
        }
        if (input.threads() < 1) {
            throw new ExecutionValidationException("threads must be at least 1");
        }
        if (input.rampUp() < 0) {
            throw new ExecutionValidationException("rampUp cannot be negative");
        }
        if (input.duration() < 1) {
            throw new ExecutionValidationException("duration must be at least 1");
        }
    }
}
