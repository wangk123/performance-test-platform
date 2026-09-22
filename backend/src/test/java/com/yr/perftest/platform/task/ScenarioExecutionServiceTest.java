package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.execution.ExecutionConfig;
import com.yr.perftest.platform.execution.aggregate.AggregateReportService;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import com.yr.perftest.platform.monitoring.ExecutionMonitorBindingService;
import com.yr.perftest.platform.task.method.PlanEvidenceImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** triggerExecution 归一化：observabilityProfile 透传落库（null → OFF，见 brief Step 1）。 */
@ExtendWith(MockitoExtension.class)
class ScenarioExecutionServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private PersistentTaskPlanRepository planRepository;
    @Mock private PersistentTaskScenarioRepository scenarioRepository;
    @Mock private PersistentScenarioExecutionRepository executionRepository;
    @Mock private com.yr.perftest.platform.auxscript.AuxScriptLifecycle auxScriptLifecycle;
    @Mock private ExecutionMonitorBindingService monitorBindingService;
    @Mock private DistributedJmeterExecutionRunner distributedJmeterExecutionRunner;
    @Mock private ScenarioExecutionRuntime executionRuntime;
    @Mock private AggregateReportService aggregateReportService;
    @Mock private ExecutionTraceQueryService executionTraceQueryService;
    @Mock private PlanEvidenceImageRepository evidenceImageRepository;

    private ScenarioExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioExecutionService(
                planRepository,
                scenarioRepository,
                executionRepository,
                auxScriptLifecycle,
                new ExecutionConfigMerger(new TaskJsonSupport(objectMapper), new ScenarioThreadGroupConfigSupport(objectMapper, null)),
                monitorBindingService,
                distributedJmeterExecutionRunner,
                executionRuntime,
                aggregateReportService,
                executionTraceQueryService,
                evidenceImageRepository,
                objectMapper
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void triggerWithDiagnosticProfilePersistedIntoConfigJson() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");
        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile("scene", 100L, "{}", 100L, null, null, "[]", null);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(scenarioRepository.findById(100L)).thenReturn(Optional.of(scenario));
        when(executionRepository.save(any())).thenAnswer(invocation -> {
            PersistentScenarioExecutionRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 77L);
            return record;
        });

        long executionId = service.triggerExecution(
                100L, "diag-run", null, null, null, ExecutionConfig.ObservabilityProfile.DIAGNOSTIC);

        assertThat(executionId).isEqualTo(77L);
        ArgumentCaptor<PersistentScenarioExecutionRecord> captor =
                ArgumentCaptor.forClass(PersistentScenarioExecutionRecord.class);
        org.mockito.Mockito.verify(executionRepository).save(captor.capture());
        ExecutionConfig persisted = readConfig(captor.getValue().getConfigJson());
        assertThat(persisted.observabilityProfile()).isEqualTo(ExecutionConfig.ObservabilityProfile.DIAGNOSTIC);
    }

    @Test
    void triggerWithoutProfileNormalizesToOffInConfigJson() {
        PersistentTaskPlanRecord plan = new PersistentTaskPlanRecord(1L, "plan", "", "admin");
        plan.updateProfile("plan", "", 10L, "[11]", "[1]");
        PersistentTaskScenarioRecord scenario = new PersistentTaskScenarioRecord(1L, 100L, "scene", 0);
        scenario.updateProfile("scene", 100L, "{}", 100L, null, null, "[]", null);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(scenarioRepository.findById(100L)).thenReturn(Optional.of(scenario));
        when(executionRepository.save(any())).thenAnswer(invocation -> {
            PersistentScenarioExecutionRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 78L);
            return record;
        });

        service.triggerExecution(100L, null, null, null, null, null);

        ArgumentCaptor<PersistentScenarioExecutionRecord> captor =
                ArgumentCaptor.forClass(PersistentScenarioExecutionRecord.class);
        org.mockito.Mockito.verify(executionRepository).save(captor.capture());
        ExecutionConfig persisted = readConfig(captor.getValue().getConfigJson());
        assertThat(persisted.observabilityProfile()).isEqualTo(ExecutionConfig.ObservabilityProfile.OFF);
    }

    private ExecutionConfig readConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, ExecutionConfig.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
