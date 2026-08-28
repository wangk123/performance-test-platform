package com.yr.perftest.platform.execution.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.ExecutionMode;
import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.JmeterScriptPatcher;
import com.yr.perftest.platform.script.JmeterScriptRenderer;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ScriptStepType;
import com.yr.perftest.platform.script.ThreadGroupConfig;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfig;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionScriptAssemblerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JmeterScriptNormalizer normalizer = new JmeterScriptNormalizer();
    private final ScenarioThreadGroupConfigSupport configSupport =
            new ScenarioThreadGroupConfigSupport(objectMapper, new JmeterScriptParser());

    private ExecutionScriptAssembler assembler() {
        return new ExecutionScriptAssembler(
                normalizer,
                new JmeterScriptParser(),
                new ThreadGroupStepPatcher(),
                new JmeterScriptPatcher(new JmeterScriptRenderer(), normalizer),
                new JmeterBackendListenerInjector(normalizer),
                configSupport
        );
    }

    @Test
    void prepareAppliesPresetPatchAndInjectsListeners(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source.jmx");
        String rendered = new JmeterScriptRenderer().render(List.of(new ScriptStepDefinition(
                "thread-1",
                ScriptStepType.THREAD_GROUP.code(),
                "线程组 1",
                new ThreadGroupConfig(100, 60, 1, 600, false).toMap(),
                List.of()
        )));
        Files.writeString(source, rendered, StandardCharsets.UTF_8);

        List<ScenarioThreadGroupConfig> presets = List.of(new ScenarioThreadGroupConfig(
                1L, "thread-1", "线程组 1", 42, 5, 120, 1, null));
        String storedJson = configSupport.writeStored(presets);
        ExecutionConfig config = new ExecutionConfig(
                42, 5, 120, 1, Map.of(), ExecutionMode.DISTRIBUTED,
                1L, List.of(1L), List.of(), null, 1, null, null);

        Path original = tempDir.resolve("original.jmx");
        Path distributed = tempDir.resolve("distributed.jmx");
        assembler().prepare(config, storedJson, source, original, distributed);

        String distributedContent = Files.readString(distributed, StandardCharsets.UTF_8);
        assertThat(distributedContent)
                .contains("<stringProp name=\"ThreadGroup.num_threads\">42</stringProp>")
                .contains("Aggregate Snapshot Collector")
                .contains("Failure Sample Collector");
    }

    @Test
    void prepareWithoutPresetKeepsOriginalThreadsAndStillInjectsListeners(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source.jmx");
        String rendered = new JmeterScriptRenderer().render(List.of(new ScriptStepDefinition(
                "thread-1",
                ScriptStepType.THREAD_GROUP.code(),
                "线程组 1",
                new ThreadGroupConfig(100, 60, 1, 600, false).toMap(),
                List.of()
        )));
        Files.writeString(source, rendered, StandardCharsets.UTF_8);

        ExecutionConfig config = new ExecutionConfig(
                100, 60, 600, 1, Map.of(), ExecutionMode.DISTRIBUTED,
                1L, List.of(1L), List.of(), null, null, null, null);

        Path original = tempDir.resolve("original.jmx");
        Path distributed = tempDir.resolve("distributed.jmx");
        assembler().prepare(config, "[]", source, original, distributed);

        String distributedContent = Files.readString(distributed, StandardCharsets.UTF_8);
        assertThat(distributedContent)
                .contains("<stringProp name=\"ThreadGroup.num_threads\">100</stringProp>")
                .contains("Aggregate Snapshot Collector");
    }
}
