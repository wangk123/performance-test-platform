package com.yr.perftest.platform.envcheck;

import java.util.Set;

/** 环境检查项 SPI：所有检查项的元数据契约（spec §3.2）。 */
public interface EnvCheckItem {

    /** 注册表唯一 key，如 os.ulimit。 */
    String key();

    /** 展示名。 */
    String label();

    /** 检查目的说明。 */
    String description();

    EnvCheckCategory category();

    EnvCheckKind kind();

    /** 适用文档模块标签，空集表示通用项。 */
    Set<String> appliesTo();

    /** 组内展示排序。 */
    int sortOrder();
}
