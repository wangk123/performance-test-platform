package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionConflictException;
import com.yr.perftest.platform.execution.ExecutionStatus;
import com.yr.perftest.platform.execution.distributed.DistributedJmeterExecutionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-control-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionControlServiceTest {
    private static final String CONFIG_JSON = "{\"threads\":1,\"rampUp\":0,\"duration\":0,\"loops\":1,\"jmeterProperties\":{},\"mode\":\"DISTRIBUTED\",\"controllerNodeId\":1,\"workerNodeIds\":[1],\"monitorTargetIds\":[]}";

    @Autowired
    private ExecutionControlService controlService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Autowired
    private ScenarioExecutionRuntime executionRuntime;

    @Autowired
    private DistributedJmeterExecutionRunner runner;

    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        plan.forceState(com.yr.perftest.platform.task.plandoc.PlanPhase.EXECUTION,
                com.yr.perftest.platform.task.plandoc.PlanStatus.PENDING);
        planRepository.save(plan);
        PersistentTaskScenarioRecord scenario = scenarioRepository.save(
                new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0));
        scenario.updateProfile("scenario-a", 1L, "{}", 1L, null, null, null);
        scenarioId = scenarioRepository.save(scenario).getId();
    }

    @Test
    void sameIdempotencyKeyStartsExecutionOnlyOnce() {
        ExecutionControlService.StartCommand command =
                new ExecutionControlService.StartCommand(scenarioId, "run-1", null, null);

        ExecutionControlService.StartOutcome first = controlService.start(command, "idem-1");
        ExecutionControlService.StartOutcome second = controlService.start(command, "idem-1");

        assertThat(first.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(first.replayed()).isFalse();
        assertThat(second.executionId()).isEqualTo(first.executionId());
        assertThat(second.replayed()).isTrue();
        assertThat(executionRepository.findAllByScenarioIdOrderByIdDesc(scenarioId)).hasSize(1);
    }

    @Test
    void cancelQueuedExecutionMarksCancelledAndRunnerDoesNotOverride() throws Exception {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        long executionId = execution.getId();
        executionRuntime.register(executionId);

        controlService.cancel(executionId);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.CANCELLED);

        runner.submit(executionId);
        Thread.sleep(500);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    void stopRunningExecutionMarksStopping() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        long executionId = executionRepository.save(execution).getId();
        executionRuntime.register(executionId);

        controlService.stop(executionId);

        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.STOPPING);
        assertThat(executionRuntime.isStopRequested(executionId)).isTrue();
    }

    @Test
    void stopAndCancelOnFinishedExecutionConflict() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));
        execution.markRunning("result.jtl", "jmeter.log");
        execution.markSuccess(0);
        long executionId = executionRepository.save(execution).getId();

        assertThatThrownBy(() -> controlService.stop(executionId))
                .isInstanceOf(ExecutionConflictException.class);
        assertThatThrownBy(() -> controlService.cancel(executionId))
                .isInstanceOf(ExecutionConflictException.class);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);
    }

    @Test
    void cancelOnMissingExecutionThrowsNotFound() {
        assertThatThrownBy(() -> controlService.cancel(999999L))
                .hasMessageContaining("execution does not exist");
    }

    @Test
    void statusReturnsCurrentExecution() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(scenarioId, CONFIG_JSON));

        ScenarioExecution view = controlService.status(execution.getId());

        assertThat(view.id()).isEqualTo(execution.getId());
        assertThat(view.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(view.scenarioId()).isEqualTo(scenarioId);
    }
}
