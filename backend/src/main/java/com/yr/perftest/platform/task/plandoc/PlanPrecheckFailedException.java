package com.yr.perftest.platform.task.plandoc;

public class PlanPrecheckFailedException extends RuntimeException {
    private final java.util.List<String> failures;

    public PlanPrecheckFailedException(String message, java.util.List<String> failures) {
        super(message);
        this.failures = java.util.List.copyOf(failures);
    }

    public java.util.List<String> getFailures() { return failures; }
}
