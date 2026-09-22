package com.yr.perftest.platform.evidence.deep;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkyWalkingTraceModelsTest {
    @Test
    void parsesBasicTracesFixture() {
        String body = """
            {"data":{"queryBasicTraces":{"traces":[
              {"traceIds":["a1b2"],"endpointNames":["POST /api/checkout"],"service":"api-gateway",
               "duration":2034,"start":"2026-09-22 143541","isError":true}
            ],"total":1}}}""";
        var list = SkyWalkingTraceModels.parseBasicTraces(body);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).traceId()).isEqualTo("a1b2");
        assertThat(list.get(0).isError()).isTrue();
        assertThat(list.get(0).durationMs()).isEqualTo(2034);
    }

    @Test
    void parsesTraceSpanTreeFixture() {
        String body = """
            {"data":{"queryTrace":{"segments":[
              {"service":"api-gateway","endpointNames":["GatewayFilterChain"],"spans":[
                {"spanId":0,"parentSpanId":-1,"endpointName":"GatewayFilterChain",
                 "startTime":1700000000000,"endTime":1700000002034,"isError":false}
              ]}
            ]}}}""";
        var detail = SkyWalkingTraceModels.parseTrace(body);
        assertThat(detail.spans()).hasSize(1);
        assertThat(detail.spans().get(0).parentSpanId()).isEqualTo(-1);
    }

    @Test
    void malformedJsonThrowsQueryException() {
        assertThatThrownBy(() -> SkyWalkingTraceModels.parseBasicTraces("{\"data\":null}"))
            .isInstanceOf(SkyWalkingQueryException.class);
    }
}
