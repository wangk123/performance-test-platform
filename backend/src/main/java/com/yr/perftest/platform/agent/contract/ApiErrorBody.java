package com.yr.perftest.platform.agent.contract;

public record ApiErrorBody(AgentErrorCode code, String message) {
}
