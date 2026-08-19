package com.yr.perftest.platform.evidence.deep;

/**
 * 深度证据源种类（T11）。每种源声明：关联键绑定、时间来源（source clock）、
 * 是否支持 traceId 关联、是否为高影响源（接入需审批）。
 */
public enum DeepEvidenceKind {
    DB_METRICS("db-metrics", "prometheus", false, false),
    TRACE("trace", "target", true, true),
    APP_LOG("app-log", "target", false, true),
    SLOW_SQL("slow-sql", "target", false, false),
    PROFILING("profiling", "target", true, false);

    private final String key;
    private final String sourceClock;
    private final boolean requiresApproval;
    private final boolean traceIdCapable;

    DeepEvidenceKind(String key, String sourceClock, boolean requiresApproval, boolean traceIdCapable) {
        this.key = key;
        this.sourceClock = sourceClock;
        this.requiresApproval = requiresApproval;
        this.traceIdCapable = traceIdCapable;
    }

    public String key() {
        return key;
    }

    public String sourceClock() {
        return sourceClock;
    }

    public boolean requiresApproval() {
        return requiresApproval;
    }

    public boolean traceIdCapable() {
        return traceIdCapable;
    }
}
