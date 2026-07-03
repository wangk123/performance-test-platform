package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.TaskExecutionResult;
import com.yr.perftest.platform.execution.aggregate.MetricTick;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.JmeterScriptRenderer;
import com.yr.perftest.platform.script.ScriptStepDefinition;
import com.yr.perftest.platform.script.ThreadGroupConfig;
import com.yr.perftest.platform.script.ThreadGroupStepPatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioThreadGroupConfigSupportTest {
  private final JmeterScriptRenderer renderer = new JmeterScriptRenderer();
  private final JmeterScriptParser parser = new JmeterScriptParser();
  private final ScenarioThreadGroupConfigSupport support = new ScenarioThreadGroupConfigSupport(new ObjectMapper(), parser);
  private final ThreadGroupStepPatcher patcher = new ThreadGroupStepPatcher();

  @Test
  void buildPatchesResolvesThreadGroupByStepNameWhenStepIdChanged() {
    String content = renderer.render(List.of(
        new ScriptStepDefinition("thread-legacy-1", "THREAD_GROUP", "线程组 1", new ThreadGroupConfig(1, 0, 1, 10, true).toMap(), List.of()),
        new ScriptStepDefinition("thread-legacy-2", "THREAD_GROUP", "线程组 2", new ThreadGroupConfig(1, 0, 1, 10, true).toMap(), List.of())
    ));
    List<ScriptStepDefinition> steps = parser.parseSteps(content);
    List<ScenarioThreadGroupConfig> preset = List.of(
        new ScenarioThreadGroupConfig(1, "thread-old-1", "线程组 1", 50, 0, 300, 0, null),
        new ScenarioThreadGroupConfig(2, "thread-old-2", "线程组 2", 80, 0, 300, 0, null)
    );

    List<ThreadGroupStepPatcher.ThreadGroupPatch> patches = support.buildPatches(steps, preset);
    List<ScriptStepDefinition> patched = patcher.patchAll(steps, patches);

    assertEquals(50, patched.get(0).threadGroupConfig().threads());
    assertEquals(80, patched.get(1).threadGroupConfig().threads());
  }

  @Test
  void summarizeAggregateRowsBySamplerLabels() {
    List<ScriptStepDefinition> steps = List.of(
        new ScriptStepDefinition(
            "thread-0",
            "THREAD_GROUP",
            "线程组 1",
            Map.of(),
            List.of(new ScriptStepDefinition("http-1", "HTTP_REQUEST", "请求A", Map.of(), List.of()))
        ),
        new ScriptStepDefinition(
            "thread-1",
            "THREAD_GROUP",
            "线程组 2",
            Map.of(),
            List.of(new ScriptStepDefinition("http-2", "HTTP_REQUEST", "请求B", Map.of(), List.of()))
        )
    );
    TaskExecutionResult result = new TaskExecutionResult(
        new TaskExecutionResult.Summary(300, 30, 5, 8, 0, "final"),
        List.of(
            new TaskExecutionResult.AggregateRow("请求A", "tg1", 100, 4, 3, 5, 6, 8, 1, 10, 0, 10),
            new TaskExecutionResult.AggregateRow("请求B", "tg2", 200, 6, 5, 7, 8, 9, 2, 12, 0, 20)
        ),
        List.of()
    );

    ThreadGroupConfigSummary first = support.summarizeThreadGroupResult(
        steps,
        new ScenarioThreadGroupConfig(1, "thread-0", "线程组 1", 10, 0, 10, 0, null),
        result
    );
    ThreadGroupConfigSummary second = support.summarizeThreadGroupResult(
        steps,
        new ScenarioThreadGroupConfig(2, "thread-1", "线程组 2", 10, 0, 10, 0, null),
        result
    );

    assertEquals(100, first.samples());
    assertEquals(10.0, first.throughput());
    assertEquals(200, second.samples());
    assertEquals(20.0, second.throughput());
  }

  @Test
  void matchesPresetBySortOrderAndConfigId() {
    List<ScenarioThreadGroupConfig> preset = List.of(
        new ScenarioThreadGroupConfig(7, "thread-0", "TG1", 10, 0, 10, 0, null),
        new ScenarioThreadGroupConfig(8, "thread-1", "TG2", 10, 0, 10, 0, null)
    );

    assertTrue(support.matchesPreset(preset, "{\"threadGroupPresetSortOrder\":0}"));
    assertTrue(support.matchesPreset(preset, "{\"threadGroupConfigId\":8}"));
    assertFalse(support.matchesPreset(preset, "{\"threadGroupPresetSortOrder\":1}"));
    assertFalse(support.matchesPreset(preset, "{\"threadGroupConfigId\":99}"));
  }

  @Test
  void findLatestMatchingExecutionSkipsUnfinishedAndStalePresets() {
    List<ScenarioThreadGroupConfig> preset = List.of(
        new ScenarioThreadGroupConfig(7, "thread-0", "TG1", 10, 0, 10, 0, null)
    );
    PersistentScenarioExecutionRecord stale = new PersistentScenarioExecutionRecord(1L, "{\"threadGroupPresetSortOrder\":1}");
    stale.markSuccess(0);
    PersistentScenarioExecutionRecord running = new PersistentScenarioExecutionRecord(1L, "{\"threadGroupPresetSortOrder\":0}");
    running.markRunning("result.jtl", "jmeter.log");
    PersistentScenarioExecutionRecord latest = new PersistentScenarioExecutionRecord(1L, "{\"threadGroupPresetSortOrder\":0}");
    latest.markSuccess(0);

    Optional<PersistentScenarioExecutionRecord> found = support.findLatestMatchingExecution(
        List.of(latest, running, stale),
        preset
    );

    assertTrue(found.isPresent());
    assertEquals(latest, found.get());
  }

  @Test
  void buildPresetSummaryAggregatesDistinctThreadGroupSummaries() {
    ThreadGroupConfigSummary summary = support.buildPresetSummary(List.of(
        new ThreadGroupConfigSummary(100, 10, 20, 0),
        new ThreadGroupConfigSummary(200, 20, 30, 1)
    ));

    assertEquals(300, summary.samples());
    assertEquals(30.0, summary.throughput());
    assertEquals(27, summary.avgRt());
    assertEquals(2.0 / 3.0, summary.errorRate(), 0.001);
  }

  @Test
  void filterMetricTicksKeepsOnlyPresetLabels() {
    List<MetricTick> ticks = List.of(new MetricTick(
        1000L,
        List.of(
            new MetricTick.LabelMetric("请求A", 10, 0, 5, 20, 30),
            new MetricTick.LabelMetric("请求B", 8, 0, 4, 25, 35)
        ),
        null
    ));

    List<MetricTick> filtered = support.filterMetricTicks(ticks, java.util.Set.of("请求A"));

    assertEquals(1, filtered.size());
    assertEquals(1, filtered.get(0).labels().size());
    assertEquals("请求A", filtered.get(0).labels().get(0).label());
  }
}
