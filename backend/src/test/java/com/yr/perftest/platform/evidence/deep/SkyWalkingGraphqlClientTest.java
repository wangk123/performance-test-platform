package com.yr.perftest.platform.evidence.deep;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkyWalkingGraphqlClientTest {
    @Test
    void unreachableEndpointThrowsWithinTimeout() {
        var props = new DeepEvidenceProperties();
        props.forKind(DeepEvidenceKind.TRACE).setEndpoint("http://127.0.0.1:1");
        var client = new SkyWalkingGraphqlClient(props);
        assertThatThrownBy(() -> client.queryBasicTraces(null, null,
            Instant.now().minusSeconds(60), Instant.now(), null, null, true, 1, 20))
          .isInstanceOf(SkyWalkingQueryException.class);
    }
}
