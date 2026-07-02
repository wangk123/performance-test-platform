package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ThreadGroupStepPatcher {
    public List<ScriptStepDefinition> patchStep(
            List<ScriptStepDefinition> steps,
            String stepId,
            int threads,
            int rampUp,
            int duration
    ) {
        return steps.stream()
                .map(step -> patchNode(step, stepId, threads, rampUp, duration))
                .toList();
    }

    public List<ScriptStepDefinition> patchAll(
            List<ScriptStepDefinition> steps,
            List<ThreadGroupPatch> patches
    ) {
        List<ScriptStepDefinition> patched = steps;
        for (ThreadGroupPatch patch : patches) {
            patched = patchStep(patched, patch.stepId(), patch.threads(), patch.rampUp(), patch.duration());
        }
        return patched;
    }

    public record ThreadGroupPatch(String stepId, int threads, int rampUp, int duration) {
    }

    private ScriptStepDefinition patchNode(
            ScriptStepDefinition step,
            String stepId,
            int threads,
            int rampUp,
            int duration
    ) {
        List<ScriptStepDefinition> children = step.children().isEmpty()
                ? step.children()
                : patchStep(step.children(), stepId, threads, rampUp, duration);
        if (!stepId.equals(step.id()) || !ScriptStepType.THREAD_GROUP.code().equals(step.type())) {
            return new ScriptStepDefinition(step.id(), step.type(), step.name(), step.config(), children);
        }
        ThreadGroupConfig config = new ThreadGroupConfig(
                threads,
                rampUp,
                1,
                duration,
                true,
                ThreadGroupConfig.MODE_DURATION,
                ThreadGroupConfig.SteppingConfig.DEFAULT
        );
        return new ScriptStepDefinition(step.id(), step.type(), step.name(), config.toMap(), children);
    }
}
