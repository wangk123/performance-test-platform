package com.yr.perftest.platform.task;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.execution.distributed.ExecutionNodeRole;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRecord;
import com.yr.perftest.platform.execution.distributed.PersistentExecutionNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution-precheck-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExecutionPrecheckServiceTest {
    @Autowired
    private ExecutionPrecheckService precheckService;

    @Autowired
    private PersistentTaskPlanRepository planRepository;

    @Autowired
    private PersistentTaskScenarioRepository scenarioRepository;

    @Autowired
    private PersistentExecutionNodeRepository nodeRepository;

    private long scenarioId;

    @BeforeEach
    void setUp() {
        PersistentTaskPlanRecord plan = planRepository.save(new PersistentTaskPlanRecord(1L, "plan-a", null, "admin"));
        scenarioId = scenarioRepository.save(new PersistentTaskScenarioRecord(plan.getId(), 1L, "scenario-a", 0)).getId();
    }

    @Test
    void missingScenarioThrowsNotFound() {
        assertThatThrownBy(() -> precheckService.precheck(999999L, null, null))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("scenario does not exist");
    }

    @Test
    void scenarioWithoutControllerNodeIsInvalid() {
        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).contains("controller node is required");
        assertThat(report.warnings()).contains("threads is not configured", "duration is not configured");
        assertThat(report.threads()).isEqualTo(0);
        assertThat(report.workerCount()).isEqualTo(0);
        assertThat(report.queueAhead()).isEqualTo(0L);
    }

    @Test
    void configuredScenarioReportsNodeAndImpact() {
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId).orElseThrow();
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), null, null, null);
        scenarioRepository.save(scenario);

        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.workerCount()).isEqualTo(1);
        assertThat(report.nodes()).hasSize(1);
        assertThat(report.nodes().get(0).nodeId()).isEqualTo(node.getId());
        assertThat(report.nodes().get(0).role()).isEqualTo("CONTROLLER");
        assertThat(report.nodes().get(0).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingWorkerNodeIsAnError() {
        PersistentExecutionNodeRecord node = nodeRepository.save(new PersistentExecutionNodeRecord(
                "node-1", "10.0.0.1", 22, "ops", "/keys/id", ExecutionNodeRole.CONTROLLER, "/tmp/perf"));
        PersistentTaskScenarioRecord scenario = scenarioRepository.findById(scenarioId).orElseThrow();
        scenario.updateProfile("scenario-a", 1L, "{}", node.getId(), "[999]", null, null);
        scenarioRepository.save(scenario);

        ExecutionPrecheckService.PrecheckReport report = precheckService.precheck(scenarioId, null, null);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(message -> message.contains("worker node 999 does not exist"));
        assertThat(report.nodes()).hasSize(2);
    }
}
