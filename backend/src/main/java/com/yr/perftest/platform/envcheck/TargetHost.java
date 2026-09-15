package com.yr.perftest.platform.envcheck;

import java.util.Set;

/** 目标机：host 地址 + 文档模块列原文（可空）。 */
public record TargetHost(String host, String module) {

    /** module 非空且任一 tag 是 module 小写形式的子串时 true；tags 空集表示通用项，恒 true。 */
    public static boolean matches(TargetHost host, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return true;
        }
        if (host == null || host.module == null || host.module.isBlank()) {
            return false;
        }
        String lower = host.module.toLowerCase();
        return tags.stream().anyMatch(lower::contains);
    }
}
