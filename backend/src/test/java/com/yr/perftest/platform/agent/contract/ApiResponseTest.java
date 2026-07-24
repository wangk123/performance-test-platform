package com.yr.perftest.platform.agent.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @Test
    void successFillsDataAndLeavesPaginationDormant() {
        ApiResponse<String> response = ApiResponse.success("req-1", "1", "payload");

        assertThat(response.requestId()).isEqualTo("req-1");
        assertThat(response.schemaVersion()).isEqualTo("1");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.error()).isNull();
        assertThat(response.warnings()).isEmpty();
        assertThat(response.truncated()).isNull();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void errorFillsErrorAndLeavesDataNull() {
        ApiErrorBody error = new ApiErrorBody(AgentErrorCode.NOT_FOUND, "missing");
        ApiResponse<String> response = ApiResponse.error("req-2", "1", error);

        assertThat(response.requestId()).isEqualTo("req-2");
        assertThat(response.schemaVersion()).isEqualTo("1");
        assertThat(response.data()).isNull();
        assertThat(response.error()).isEqualTo(error);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.truncated()).isNull();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void agentErrorCodeContainsFullStableSet() {
        assertThat(AgentErrorCode.values()).containsExactlyInAnyOrder(
                AgentErrorCode.AUTHENTICATION_FAILED,
                AgentErrorCode.ACCESS_DENIED,
                AgentErrorCode.NOT_FOUND,
                AgentErrorCode.DATA_SOURCE_UNAVAILABLE,
                AgentErrorCode.QUERY_TOO_LARGE,
                AgentErrorCode.TIMEOUT,
                AgentErrorCode.RATE_LIMITED,
                AgentErrorCode.IDEMPOTENCY_CONFLICT,
                AgentErrorCode.EXECUTION_CONFLICT,
                AgentErrorCode.VALIDATION_FAILED,
                AgentErrorCode.INTERNAL_ERROR
        );
    }
}
