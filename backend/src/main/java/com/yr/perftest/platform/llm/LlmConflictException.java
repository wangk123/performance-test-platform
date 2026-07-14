package com.yr.perftest.platform.llm;

public class LlmConflictException extends RuntimeException {
    public LlmConflictException(String message) {
        super(message);
    }
}
