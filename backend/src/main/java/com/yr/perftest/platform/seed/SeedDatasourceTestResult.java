package com.yr.perftest.platform.seed;

/**
 * 数据源连通性测试结果（替代裸 Map 返回，JSON 键与旧契约一致：ok / message）。
 */
public record SeedDatasourceTestResult(
        boolean ok,
        String message
) {
}
