package com.yr.perftest.platform.script;

/**
 * Enumeration of all supported JMeter script step types.
 * Each value carries a {@code code} (used in JSON/API transport)
 * and a {@code displayName} (human-readable label in Chinese).
 */
public enum ScriptStepType {

    THREAD_GROUP("THREAD_GROUP", "线程组配置"),
    HTTP_REQUEST("HTTP_REQUEST", "HTTP 请求"),
    CSV_DATA("CSV_DATA", "CSV 数据文件"),
    USER_PARAMS("USER_PARAMS", "用户参数"),
    HEADER_CONFIG("HEADER_CONFIG", "Header 头配置"),
    RESPONSE_ASSERTION("ASSERTION", "响应断言"),
    JSR223_PRE_PROCESSOR("JSR223_PRE_PROCESSOR", "JSR223 前置处理器"),
    JSR223_POST_PROCESSOR("JSR223_POST_PROCESSOR", "JSR223 后置处理器");

    private final String code;
    private final String displayName;

    ScriptStepType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Look up a step type by its transport code.
     *
     * @param code the code string (e.g. "THREAD_GROUP")
     * @return the matching enum value, or {@code null} if unknown
     */
    public static ScriptStepType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScriptStepType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
