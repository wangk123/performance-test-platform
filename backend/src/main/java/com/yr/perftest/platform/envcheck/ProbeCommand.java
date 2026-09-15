package com.yr.perftest.platform.envcheck;

/** TARGET 探测命令：一次 env-probe 连接内的探测项标识 + shell 片段。 */
public record ProbeCommand(String id, String script) {
}
