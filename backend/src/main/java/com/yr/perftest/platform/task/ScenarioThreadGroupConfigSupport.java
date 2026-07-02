package com.yr.perftest.platform.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptStepType;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    input.sortOrder(),
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

    public List<ScenarioThreadGroupConfig> presetConfigs(List<ScenarioThreadGroupConfig> configs, long configId) {
        ScenarioThreadGroupConfig selected = requireConfig(configs, configId);
        return presetConfigsBySortOrder(configs, selected.sortOrder());
    }

    public List<ScenarioThreadGroupConfig> presetConfigsBySortOrder(List<ScenarioThreadGroupConfig> configs, int sortOrder) {
        return configs.stream()
                .filter(config -> config.sortOrder() == sortOrder)
                .toList();
    }

    public List<ThreadGroupStepPatcher.ThreadGroupPatch> buildPatches(
            List<ScriptStepDefinition> steps,
            List<ScenarioThreadGroupConfig> configs
    ) {
        Map<String, ScriptStepDefinition> threadGroupsById = new LinkedHashMap<>();
        Map<String, String> idByName = new LinkedHashMap<>();
        collectThreadGroupIndex(steps, threadGroupsById, idByName);

        List<ThreadGroupStepPatcher.ThreadGroupPatch> patches = new ArrayList<>();
        for (ScenarioThreadGroupConfig config : configs) {
            String resolvedStepId = resolveThreadGroupStepId(config, threadGroupsById, idByName);
            if (resolvedStepId == null) {
                throw new ExecutionValidationException("thread group step does not exist: " + config.stepName());
            }
            patches.add(new ThreadGroupStepPatcher.ThreadGroupPatch(
                    resolvedStepId,
                    config.threads(),
                    config.rampUp(),
                    config.duration()
            ));
        }
        return patches;
    }

    public List<ScriptStepDefinition> loadScriptSteps(Path scriptPath) {
        try {
            String content = Files.readString(scriptPath, StandardCharsets.UTF_8);
            return scriptParser.parseSteps(content);
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to read script steps");
        }
    }

    public ThreadGroupConfigSummary summarizeThreadGroupResult(
            List<ScriptStepDefinition> steps,
            ScenarioThreadGroupConfig config,
            TaskExecutionResult result
    ) {
        if (result == null || result.summary() == null || result.summary().samples() <= 0) {
            return null;
        }
        Set<String> labels = collectSamplerLabels(steps, config.stepId(), config.stepName());
        if (!labels.isEmpty() && result.aggregateRows() != null && !result.aggregateRows().isEmpty()) {
            ThreadGroupConfigSummary scoped = summarizeAggregateRows(result.aggregateRows(), labels);
            if (scoped != null) {
                return scoped;
            }
        }
        TaskExecutionResult.Summary summary = result.summary();
        return new ThreadGroupConfigSummary(
                summary.samples(),
                summary.throughput(),
                summary.avgRt(),
                summary.errorRate()
        );
    }

    public Set<String> collectSamplerLabels(
            List<ScriptStepDefinition> steps,
            String threadGroupStepId,
            String threadGroupStepName
    ) {
        ScriptStepDefinition group = findThreadGroup(steps, threadGroupStepId, threadGroupStepName);
        if (group == null) {
            return Set.of();
        }
        Set<String> labels = new LinkedHashSet<>();
        collectHttpSamplerLabels(group.children(), labels);
        return labels;
    }

    public ThreadGroupConfigSummary summarizeAggregateRows(
            List<TaskExecutionResult.AggregateRow> rows,
            Set<String> labels
    ) {
        int samples = 0;
        double throughput = 0;
        long weightedRt = 0;
        double weightedError = 0;
        for (TaskExecutionResult.AggregateRow row : rows) {
            if (!labels.contains(row.label())) {
                continue;
            }
            samples += row.samples();
            throughput += row.throughput();
            weightedRt += row.average() * row.samples();
            weightedError += row.errorRate() * row.samples();
        }
        if (samples <= 0) {
            return null;
        }
        return new ThreadGroupConfigSummary(
                samples,
                throughput,
                Math.round((double) weightedRt / samples),
                weightedError / samples
        );
    }

    private ScriptStepDefinition findThreadGroup(
            List<ScriptStepDefinition> steps,
            String threadGroupStepId,
            String threadGroupStepName
    ) {
        for (ScriptStepDefinition step : steps) {
            if (ScriptStepType.THREAD_GROUP.code().equals(step.type())) {
                if (threadGroupStepId != null && threadGroupStepId.equals(step.id())) {
                    return step;
                }
                if (threadGroupStepName != null && threadGroupStepName.equals(step.name())) {
                    return step;
                }
            }
            if (!step.children().isEmpty()) {
                ScriptStepDefinition nested = findThreadGroup(step.children(), threadGroupStepId, threadGroupStepName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private void collectHttpSamplerLabels(List<ScriptStepDefinition> steps, Set<String> labels) {
        for (ScriptStepDefinition step : steps) {
            if (ScriptStepType.HTTP_REQUEST.code().equals(step.type())) {
                labels.add(step.name());
            }
            if (!step.children().isEmpty()) {
                collectHttpSamplerLabels(step.children(), labels);
            }
        }
    }

    private void collectThreadGroupIndex(
            List<ScriptStepDefinition> steps,
            Map<String, ScriptStepDefinition> threadGroupsById,
            Map<String, String> idByName
    ) {
        for (ScriptStepDefinition step : steps) {
            if (ScriptStepType.THREAD_GROUP.code().equals(step.type())) {
                threadGroupsById.put(step.id(), step);
                idByName.put(step.name(), step.id());
            }
            if (!step.children().isEmpty()) {
                collectThreadGroupIndex(step.children(), threadGroupsById, idByName);
            }
        }
    }

    private String resolveThreadGroupStepId(
            ScenarioThreadGroupConfig config,
            Map<String, ScriptStepDefinition> threadGroupsById,
            Map<String, String> idByName
    ) {
        if (config.stepId() != null && threadGroupsById.containsKey(config.stepId())) {
            return config.stepId();
        }
        if (config.stepName() != null && idByName.containsKey(config.stepName())) {
            return idByName.get(config.stepName());
        }
        return null;
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
