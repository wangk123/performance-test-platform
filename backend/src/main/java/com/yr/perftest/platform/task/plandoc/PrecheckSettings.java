package com.yr.perftest.platform.task.plandoc;

import java.util.List;
import java.util.Map;

/** 环境检查执行设置（非文档内容，不进评审不进 revision；设计 §10.2）。items 语义为注册表 key，读取时惰性迁移（spec §3.4）。 */
public record PrecheckSettings(boolean enabled, List<String> items) {

    public static final List<String> DEFAULT_ITEMS =
            List.of("doc.metrics-defined", "doc.scenarios-configured", "doc.script-bound");

    /** 旧中文 → key 惰性迁移映射表（spec §3.4）；人工四项映射为 "" 表示丢弃（spec E2：人工确认项移出环境检查，不再拦截执行）。 */
    public static final Map<String, String> LEGACY_KEY_MAP = Map.of(
            "指标已定义", "doc.metrics-defined",
            "场景已配置", "doc.scenarios-configured",
            "脚本已关联", "doc.script-bound",
            "环境就绪", "",
            "数据就绪", "",
            "人员到位", "",
            "接口人明确", "");

    /** 读取 precheck_json 后惰性迁移：旧中文换 key、人工项丢弃、已是 key 原样；空结果回退默认项。 */
    public static PrecheckSettings migrate(PrecheckSettings raw) {
        if (raw == null || raw.items() == null) {
            return new PrecheckSettings(raw == null || !raw.enabled(), DEFAULT_ITEMS);
        }
        List<String> mapped = raw.items().stream()
                .map(item -> LEGACY_KEY_MAP.getOrDefault(item, item))
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return new PrecheckSettings(raw.enabled(), mapped.isEmpty() ? DEFAULT_ITEMS : mapped);
    }

    public static PrecheckSettings disabled() {
        return new PrecheckSettings(false, DEFAULT_ITEMS);
    }
}
