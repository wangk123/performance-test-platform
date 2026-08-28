package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.JmeterScriptPatcher;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 执行脚本装配：装载 → 线程组预设补丁 → 监听器注入 → 写出。
 * 唯一的调用方是 {@link DistributedJmeterExecutionRunner}，装配语义不再散落在编排代码里。
 */
@Service
public class ExecutionScriptAssembler {
    private static final Path SNAPSHOT_BIN_PATH = Path.of("/test/aggregate-snapshot.bin");

    private final JmeterScriptNormalizer scriptNormalizer;
    private final JmeterScriptParser scriptParser;
    private final ThreadGroupStepPatcher threadGroupStepPatcher;
    private final JmeterScriptPatcher scriptPatcher;
    private final JmeterBackendListenerInjector backendListenerInjector;
    private final ScenarioThreadGroupConfigSupport threadGroupConfigSupport;

    public ExecutionScriptAssembler(
            JmeterScriptNormalizer scriptNormalizer,
            JmeterScriptParser scriptParser,
            ThreadGroupStepPatcher threadGroupStepPatcher,
            JmeterScriptPatcher scriptPatcher,
            JmeterBackendListenerInjector backendListenerInjector,
            ScenarioThreadGroupConfigSupport threadGroupConfigSupport
    ) {
        this.scriptNormalizer = scriptNormalizer;
        this.scriptParser = scriptParser;
        this.threadGroupStepPatcher = threadGroupStepPatcher;
        this.scriptPatcher = scriptPatcher;
        this.backendListenerInjector = backendListenerInjector;
        this.threadGroupConfigSupport = threadGroupConfigSupport;
    }

    public void prepare(
            ExecutionConfig config,
            String storedThreadGroupConfigsJson,
            Path sourcePath,
            Path originalTestPlanPath,
            Path distributedTestPlanPath
    ) {
        try {
            scriptNormalizer.copyNormalized(sourcePath, originalTestPlanPath);
            applyThreadGroupPresets(config, storedThreadGroupConfigsJson, originalTestPlanPath);
            backendListenerInjector.inject(originalTestPlanPath, distributedTestPlanPath, SNAPSHOT_BIN_PATH);
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to prepare execution script: " + exception.getMessage());
        }
    }

    private void applyThreadGroupPresets(
            ExecutionConfig config,
            String storedThreadGroupConfigsJson,
            Path testPlanPath
    ) throws Exception {
        if (config == null) {
            return;
        }
        List<ScenarioThreadGroupConfig> stored = threadGroupConfigSupport.readStored(storedThreadGroupConfigsJson);
        List<ScenarioThreadGroupConfig> presetRows = List.of();
        if (config.threadGroupPresetSortOrder() != null) {
            presetRows = threadGroupConfigSupport.presetConfigsBySortOrder(stored, config.threadGroupPresetSortOrder());
        } else if (config.threadGroupConfigId() != null) {
            presetRows = threadGroupConfigSupport.presetConfigs(stored, config.threadGroupConfigId());
        } else if (config.stepId() != null && !config.stepId().isBlank() && config.threads() > 0) {
            presetRows = List.of(new ScenarioThreadGroupConfig(
                    config.threadGroupConfigId() != null ? config.threadGroupConfigId() : 0L,
                    config.stepId(),
                    config.stepName() != null ? config.stepName() : "",
                    config.threads(),
                    config.rampUp(),
                    config.duration(),
                    0,
                    null
            ));
        }
        if (presetRows.isEmpty()) {
            return;
        }
        String content = Files.readString(testPlanPath, StandardCharsets.UTF_8);
        List<ScriptStepDefinition> steps = scriptParser.parseSteps(content);
        List<ThreadGroupStepPatcher.ThreadGroupPatch> patches = threadGroupConfigSupport.buildPatches(steps, presetRows);
        if (patches.isEmpty()) {
            return;
        }
        List<ScriptStepDefinition> patched = threadGroupStepPatcher.patchAll(steps, patches);
        Files.writeString(testPlanPath, scriptPatcher.patch(content, patched), StandardCharsets.UTF_8);
    }
}
