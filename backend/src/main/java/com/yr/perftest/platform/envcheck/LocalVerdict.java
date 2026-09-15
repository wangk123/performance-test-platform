package com.yr.perftest.platform.envcheck;

/** LOCAL 检查结论：ok=false 时 detail 必填（spec §3.2）。 */
public record LocalVerdict(boolean ok, String detail) {
}
