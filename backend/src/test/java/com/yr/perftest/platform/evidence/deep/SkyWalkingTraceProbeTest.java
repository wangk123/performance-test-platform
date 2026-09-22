package com.yr.perftest.platform.evidence.deep;

import com.yr.perftest.platform.evidence.CorrelationKey;
import com.yr.perftest.platform.facade.query.Availability;
import com.yr.perftest.platform.facade.query.PageBudget;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyWalkingTraceProbeTest {

    @Test
    void listPathBuildsAvailabilityAndBudgetTruncation() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(100)))
                .thenReturn(List.of(new SkyWalkingTraceModels.TraceBrief(
                        "t1", "api-gateway", "GET /a", 1_000L, 500, false, 3)));
        SkyWalkingTraceProbe probe = new SkyWalkingTraceProbe(client);

        DeepProbeResult result = probe.probe(new CorrelationKey(
                1L, Instant.now().minusSeconds(60), Instant.now(), List.of(), null, null),
                PageBudget.defaults());

        assertThat(result.availability().present()).isTrue();
        assertThat(result.availability().missingReason()).isNull();
        assertThat(result.availability().truncated()).isFalse();
        assertThat(result.availability().sourceRef()).startsWith("skywalking:trace?from=");
        assertThat(result.summary().get("traceCount")).isEqualTo(1);
        assertThat(result.summary().get("errorCount")).isEqualTo(0L);
        assertThat(result.summary().get("slowestMs")).isEqualTo(500L);
    }

    @Test
    void emptyResultReportsNoData() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(100)))
                .thenReturn(List.of());
        SkyWalkingTraceProbe probe = new SkyWalkingTraceProbe(client);

        DeepProbeResult result = probe.probe(new CorrelationKey(
                1L, Instant.now().minusSeconds(60), Instant.now(), List.of(), null, null),
                PageBudget.defaults());

        assertThat(result.availability().present()).isFalse();
        assertThat(result.availability().missingReason()).isEqualTo(Availability.MissingReason.NO_DATA);
        assertThat(result.summary().get("traceCount")).isEqualTo(0);
    }

    @Test
    void runtimeFailureReportsSourceUnavailable() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        when(client.queryBasicTraces(any(), any(), any(), any(), any(), any(), anyBoolean(), eq(1), eq(100)))
                .thenThrow(new SkyWalkingQueryException("skywalking graphql request failed: connect refused"));
        SkyWalkingTraceProbe probe = new SkyWalkingTraceProbe(client);

        DeepProbeResult result = probe.probe(new CorrelationKey(
                1L, Instant.now().minusSeconds(60), Instant.now(), List.of(), null, null),
                PageBudget.defaults());

        assertThat(result.availability().present()).isFalse();
        assertThat(result.availability().missingReason()).isEqualTo(Availability.MissingReason.SOURCE_UNAVAILABLE);
        assertThat(result.sourceRef()).isEqualTo("skywalking:trace");
        assertThat(String.valueOf(result.summary().get("error"))).contains("connect refused");
    }

    @Test
    void spanTreeTruncatedToBudget() {
        SkyWalkingGraphqlClient client = mock(SkyWalkingGraphqlClient.class);
        List<SkyWalkingTraceModels.TraceSpanView> spans = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            spans.add(new SkyWalkingTraceModels.TraceSpanView(
                    "seg-" + i / 10, i % 10, -1, "svc-" + i % 3, "GET /op",
                    1_000L + i, 1_005L + i, i % 7 == 0, ""));
        }
        when(client.queryTrace("t-1")).thenReturn(new SkyWalkingTraceModels.TraceDetail(
                new SkyWalkingTraceModels.TraceBrief("t-1", "api-gateway", "GET /a", 1_000L, 1_200, false, 1200),
                spans));
        SkyWalkingTraceProbe probe = new SkyWalkingTraceProbe(client);

        DeepProbeResult result = probe.probe(new CorrelationKey(
                1L, Instant.now().minusSeconds(60), Instant.now(), List.of(), null, "t-1"),
                new PageBudget(1000, 1_048_576, 3000));

        assertThat(result.availability().present()).isTrue();
        assertThat(result.availability().missingReason()).isNull();
        assertThat(result.availability().truncated()).isTrue();
        assertThat(result.availability().granularity()).isEqualTo("span");
        assertThat(result.summary().get("spans")).isEqualTo(1000);
        assertThat(result.summary().get("truncated")).isEqualTo(true);
        assertThat(result.summary().get("services")).isEqualTo(3);
    }
}
