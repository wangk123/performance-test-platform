package com.yr.perftest.platform.task.plandoc;

public class PlanStateException extends RuntimeException {
    private final PlanStatus status;
    private final java.util.List<String> allowedActions;

    public PlanStateException(String message, PlanStatus status, java.util.List<String> allowedActions) {
        super(message);
        this.status = status;
        this.allowedActions = java.util.List.copyOf(allowedActions);
    }

    public PlanStatus getStatus() { return status; }
    public java.util.List<String> getAllowedActions() { return allowedActions; }
}
