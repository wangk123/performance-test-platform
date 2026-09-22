package com.yr.perftest.platform.evidence.deep;

/**
 * SkyWalking OAP GraphQL 查询失败（HTTP 非 200 / GraphQL errors / 响应解析失败）。
 */
public class SkyWalkingQueryException extends RuntimeException {
    public SkyWalkingQueryException(String message) {
        super(message);
    }

    public SkyWalkingQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
