package com.yr.perftest.platform.envcheck;

/** 探测输出：目标机标识 + 退出码 + stdout。 */
public record ProbeOutput(String host, int exitCode, String stdout) {
}
