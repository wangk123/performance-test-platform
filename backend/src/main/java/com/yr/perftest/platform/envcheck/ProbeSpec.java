package com.yr.perftest.platform.envcheck;

/** 探测声明：执行位置 + shell 片段（约定输出单行 JSON，spec §3.2）。 */
public record ProbeSpec(ProbeLocation location, String script) {
}
