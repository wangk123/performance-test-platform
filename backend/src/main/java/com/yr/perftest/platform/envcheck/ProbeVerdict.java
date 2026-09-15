package com.yr.perftest.platform.envcheck;

/** 探测判定结论：ok=true 时 suggestion/method 为 null（spec §3.2）。 */
public record ProbeVerdict(boolean ok, String detail, String suggestion, String method) {
}
