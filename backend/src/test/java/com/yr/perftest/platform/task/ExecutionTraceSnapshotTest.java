package com.yr.perftest.platform.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceKind;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceProperties;
import com.yr.perftest.platform.evidence.deep.SkyWalkingGraphqlClient;
import com.yr.perftest.platform.evidence.deep.SkyWalkingQueryException;
import com.yr.perftest.platform.evidence.deep.SkyWalkingTraceModels;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 终态 trace 快照（Task 6）：captureSnapshot 拉 Top500 耗时 trace 落 JSON 快照，
 * OAP 失败静默（终态收尾不得因快照失败中断）。repository 用 H2 真库断言。
 */
@DataJpaTest
class ExecutionTraceSnapshotTest {
    @Autowired
    private PersistentExecutionTraceSnapshotRepository snapshotRepository;
    @Autowired
    private PersistentScenarioExecutionRepository executionRepository;

    @Test
    void capturePullsTop500ByDurationAndPersistsJson() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(500)))
                .thenReturn(List.of(
                        brief("t-1", 900),
                        brief("t-2", 600),
                        brief("t-3", 300)));
        long executionId = terminalExecution();
        ExecutionTraceQueryService service = service(client, configuredProperties());

        service.captureSnapshot(executionId);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(client).queryBasicTraces(
                any(), any(), from.capture(), to.capture(), any(), any(), eq(true), eq(1), eq(500));
        PersistentScenarioExecutionRecord execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(from.getValue()).isEqualTo(execution.getStartTime());
        assertThat(to.getValue()).isEqualTo(execution.getEndTime());

        Optional<PersistentExecutionTraceSnapshotRecord> snapshot = snapshotRepository.findByExecutionId(executionId);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getTracesJson()).contains("t-1", "t-2", "t-3");
        assertThat(snapshot.get().getTotal()).isEqualTo(3);
        assertThat(snapshot.get().getCapturedAt()).isCloseTo(Instant.now(), within(java.time.Duration.ofSeconds(60)));
    }

    @Test
    void recaptureReplacesPreviousSnapshot() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(500)))
                .thenReturn(List.of(brief("t-1", 900)))
                .thenReturn(List.of(brief("t-9", 100)));
        long executionId = terminalExecution();
        ExecutionTraceQueryService service = service(client, configuredProperties());

        service.captureSnapshot(executionId);
        service.captureSnapshot(executionId);

        List<PersistentExecutionTraceSnapshotRecord> rows = snapshotRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getExecutionId()).isEqualTo(executionId);
        assertThat(rows.get(0).getTracesJson()).contains("t-9").doesNotContain("t-1");
        assertThat(rows.get(0).getTotal()).isEqualTo(1);
    }

    @Test
    void captureSilentlyIgnoresOapFailure() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(500)))
                .thenThrow(new SkyWalkingQueryException("skywalking graphql request failed: connect refused"));
        long executionId = terminalExecution();
        ExecutionTraceQueryService service = service(client, configuredProperties());

        service.captureSnapshot(executionId);

        assertThat(snapshotRepository.findByExecutionId(executionId)).isEmpty();
    }

    @Test
    void captureSkipsWhenTraceEndpointUnconfigured() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        long executionId = terminalExecution();
        ExecutionTraceQueryService service = service(client, new DeepEvidenceProperties());

        service.captureSnapshot(executionId);

        verify(client, never()).queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(500));
        assertThat(snapshotRepository.findByExecutionId(executionId)).isEmpty();
    }

    @Test
    void deleteByExecutionIdRemovesSnapshot() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(500)))
                .thenReturn(List.of(brief("t-1", 900)));
        long executionId = terminalExecution();
        ExecutionTraceQueryService service = service(client, configuredProperties());
        service.captureSnapshot(executionId);
        assertThat(snapshotRepository.findByExecutionId(executionId)).isPresent();

        service.deleteByExecutionId(executionId);

        assertThat(snapshotRepository.findByExecutionId(executionId)).isEmpty();
    }

    private ExecutionTraceQueryService service(SkyWalkingGraphqlClient client, DeepEvidenceProperties properties) {
        return new ExecutionTraceQueryService(
                snapshotRepository,
                executionRepository,
                client,
                properties,
                new ObjectMapper());
    }

    private DeepEvidenceProperties configuredProperties() {
        DeepEvidenceProperties properties = new DeepEvidenceProperties();
        properties.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://skywalking-oap/graphql");
        return properties;
    }

    private long terminalExecution() {
        PersistentScenarioExecutionRecord execution = executionRepository.save(
                new PersistentScenarioExecutionRecord(1L, "{}"));
        execution.markRunning("/tmp/result.jtl", "/tmp/jmeter.log");
        execution.markSuccess(0);
        return executionRepository.save(execution).getId();
    }

    private SkyWalkingTraceModels.TraceBrief brief(String traceId, long durationMs) {
        return new SkyWalkingTraceModels.TraceBrief(traceId, "api-gateway", "GET /a", 1_000L, durationMs, false, 3);
    }
}
