package com.yr.perftest.platform.envcheck;

/** 探测结果：探测项标识 + 退出码 + stdout。 */
public record ProbeOutcome(String id, int code, String output) {
}
