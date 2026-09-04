package com.yr.perftest.platform.task.plandoc;

public class PlanRevisionConflictException extends RuntimeException {
    private final int currentRevision;
    private final String serverMarkdown;

    public PlanRevisionConflictException(String message, int currentRevision, String serverMarkdown) {
        super(message);
        this.currentRevision = currentRevision;
        this.serverMarkdown = serverMarkdown;
    }

    public int getCurrentRevision() { return currentRevision; }
    public String getServerMarkdown() { return serverMarkdown; }
}
