package com.yr.perftest.platform.envcheck.items;

import com.yr.perftest.platform.envcheck.EnvCheckCategory;
import com.yr.perftest.platform.envcheck.EnvCheckKind;
import com.yr.perftest.platform.envcheck.LocalCheckContext;
import com.yr.perftest.platform.envcheck.LocalVerdict;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceKind;
import com.yr.perftest.platform.evidence.deep.DeepEvidenceProperties;
import com.yr.perftest.platform.evidence.deep.SkyWalkingGraphqlClient;
import com.yr.perftest.platform.evidence.deep.SkyWalkingQueryException;
import com.yr.perftest.platform.evidence.deep.SkyWalkingTraceModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 链路上报探测四态：未启用跳过 / 有上报 / 无上报 WARNING / OAP 不可达 WARNING（软检查，不阻断执行）。 */
class TraceAgentReportingItemTest {
    private final SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
    private final DeepEvidenceProperties properties = new DeepEvidenceProperties();
    private final TraceAgentReportingItem item = new TraceAgentReportingItem(properties, client);
    private final LocalCheckContext ctx = new LocalCheckContext("", List.of());

    @Test
    void disabledSourceSkipsOk() {
        LocalVerdict verdict = item.check(ctx);

        assertThat(verdict.ok()).isTrue();
        assertThat(verdict.detail()).contains("未启用");
        verify(client, never()).queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void recentTracesReportedOk() {
        properties.forKind(DeepEvidenceKind.TRACE).setEnabled(true);
        properties.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://skywalking-oap/graphql");
        when(client.queryBasicTraces(
                isNull(), isNull(), any(Instant.class), any(Instant.class), isNull(), isNull(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of(new SkyWalkingTraceModels.TraceBrief("t-1", "svc", "/api", 0L, 12L, false, 2)));

        LocalVerdict verdict = item.check(ctx);

        assertThat(verdict.ok()).isTrue();
    }

    @Test
    void noRecentTracesWarns() {
        properties.forKind(DeepEvidenceKind.TRACE).setEnabled(true);
        properties.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://skywalking-oap/graphql");
        when(client.queryBasicTraces(
                isNull(), isNull(), any(Instant.class), any(Instant.class), isNull(), isNull(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(List.of());

        LocalVerdict verdict = item.check(ctx);

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("agent 未挂载或采样率为 0");
    }

    @Test
    void oapUnreachableWarns() {
        properties.forKind(DeepEvidenceKind.TRACE).setEnabled(true);
        properties.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://skywalking-oap/graphql");
        when(client.queryBasicTraces(
                isNull(), isNull(), any(Instant.class), any(Instant.class), isNull(), isNull(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new SkyWalkingQueryException("skywalking graphql request failed: connect timed out"));

        LocalVerdict verdict = item.check(ctx);

        assertThat(verdict.ok()).isFalse();
        assertThat(verdict.detail()).contains("OAP 不可达");
    }

    @Test
    void metadataBindsObservabilityCategory() {
        assertThat(item.key()).isEqualTo("trace.agent-reporting");
        assertThat(item.label()).isEqualTo("链路上报探测");
        assertThat(item.category()).isEqualTo(EnvCheckCategory.OBSERVABILITY);
        assertThat(item.kind()).isEqualTo(EnvCheckKind.LOCAL);
        assertThat(item.appliesTo()).isEmpty();
    }
}
