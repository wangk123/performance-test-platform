package com.yr.perftest.platform.task.plandoc;

import java.util.List;

/** 环境检查执行设置（非文档内容，不进评审不进 revision；设计 §10.2）。 */
public record PrecheckSettings(boolean enabled, List<String> items) {
    public static final List<String> DEFAULT_ITEMS =
            List.of("指标已定义", "场景已配置", "脚本已关联", "环境就绪", "数据就绪", "人员到位", "接口人明确");

    public static PrecheckSettings disabled() {
        return new PrecheckSettings(false, DEFAULT_ITEMS);
    }
}
