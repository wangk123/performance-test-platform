package com.yr.perftest.platform.envcheck;

import java.util.List;

/** LOCAL 检查上下文：计划正文与场景行。 */
public record LocalCheckContext(String body, List<ScenarioRow> scenarios) {

    /** 场景行：场景名与关联脚本版本。 */
    public record ScenarioRow(String name, Long scriptVersionId) {
    }
}
