package com.yr.perftest.platform.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.task.PersistentScenarioExecutionRepository;
import com.yr.perftest.platform.script.PersistentScriptVersionRepository;
import com.yr.perftest.platform.task.PersistentTaskScenarioRecord;
import com.yr.perftest.platform.task.PersistentTaskScenarioRepository;
import com.yr.perftest.platform.task.ScenarioExecutionService;
import com.yr.perftest.platform.task.ScenarioThreadGroupConfigSupport;
import com.yr.perftest.platform.script.JmeterScriptParser;
import com.yr.perftest.platform.script.ScriptService;
import com.yr.perftest.platform.task.TaskPlan;
import com.yr.perftest.platform.task.TaskPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDataServiceTest {
  @Mock
  private TaskPlanService planService;
  @Mock
  private ScenarioExecutionService executionService;
  @Mock
  private ScriptService scriptService;
  @Mock
  private PersistentTaskScenarioRepository scenarioRepository;
  @Mock
  private PersistentScenarioExecutionRepository executionRepository;
  @Mock
  private PersistentScriptVersionRepository scriptVersionRepository;

  private ReportDataService reportDataService;

  @BeforeEach
  void setUp() {
    ScenarioThreadGroupConfigSupport configSupport =
        new ScenarioThreadGroupConfigSupport(new ObjectMapper(), new JmeterScriptParser());
    reportDataService = new ReportDataService(
        planService,
        executionService,
        scriptService,
        scenarioRepository,
        executionRepository,
        scriptVersionRepository,
        configSupport
    );
  }

  @Test
  void aggregateByPlanReturnsEmptyPresetsWhenScenarioHasNoThreadGroupConfigs() throws Exception {
    TaskPlan plan = new TaskPlan(1L, 10L, "plan-a", "", null, null, null, null, null, null, 0);
    PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 99L, "scenario-a", 0);
    setScenarioId(scenario, 1L);

    when(planService.getPlan(1L)).thenReturn(plan);
    when(scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(scenario));

    PlanReportResponse response = reportDataService.aggregateByPlan(1L);

    assertEquals(1, response.scenarios().size());
    assertTrue(response.scenarios().get(0).presets().isEmpty());
  }

  @Test
  void aggregateByPlanBuildsPresetRowsForMultiThreadGroupConfig() throws Exception {
    TaskPlan plan = new TaskPlan(1L, 10L, "plan-a", "", null, null, null, null, null, null, 0);
    PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 99L, "scenario-a", 0);
    setScenarioId(scenario, 1L);
    scenario.updateProfile(
        "scenario-a",
        99L,
        "{}",
        null,
        null,
        null,
        "["
            + "{\"id\":7,\"stepId\":\"thread-0\",\"stepName\":\"TG1\",\"threads\":10,\"rampUp\":0,\"duration\":10,\"sortOrder\":0},"
            + "{\"id\":8,\"stepId\":\"thread-1\",\"stepName\":\"TG2\",\"threads\":10,\"rampUp\":0,\"duration\":10,\"sortOrder\":0}"
            + "]"
    );

    when(planService.getPlan(1L)).thenReturn(plan);
    when(scenarioRepository.findAllByPlanIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(scenario));
    when(executionRepository.findAllByScenarioIdOrderByIdDesc(1L)).thenReturn(List.of());
    when(scriptService.getScriptDefinition(anyLong(), anyLong())).thenReturn(null);

    PlanReportResponse response = reportDataService.aggregateByPlan(1L);
    PlanReportResponse.PresetReport preset = response.scenarios().get(0).presets().get(0);

    assertEquals(2, preset.rows().size());
    assertEquals("TG1", preset.rows().get(0).stepName());
    assertEquals("TG2", preset.rows().get(1).stepName());
    assertNull(preset.summary());
    assertNull(preset.executionId());
    assertTrue(preset.aggregateRows().isEmpty());
    assertTrue(preset.metricSeries().ticks().isEmpty());
  }

  private static void setScenarioId(PersistentTaskScenarioRecord scenario, long id) throws Exception {
    var field = PersistentTaskScenarioRecord.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(scenario, id);
  }
}
