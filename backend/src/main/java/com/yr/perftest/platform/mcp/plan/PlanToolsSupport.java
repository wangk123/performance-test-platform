package com.yr.perftest.platform.mcp.plan;

import com.yr.perftest.platform.identity.HumanPrincipal;
import com.yr.perftest.platform.identity.SystemRole;

import java.util.Map;
import java.util.Set;

/**
 * 计划工具共享支撑（P0-2①）：机器身份 → 服务层身份映射与入参解析。
 * 身份映射口径见 spec §6.1 决策 9——管理员签发 Key 即受信操作者，
 * 授权边界 = API Key scope + 治理层 + D12 白名单，不做按项目成员判定。
 */
final class PlanToolsSupport {
    static final String AGENT_USERNAME = "agent";

    private PlanToolsSupport() {
    }

    static HumanPrincipal agentActor() {
        return new HumanPrincipal(AGENT_USERNAME, Set.of(SystemRole.ADMIN));
    }

    static long requiredLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return number.longValue();
    }

    static String requiredString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return string;
    }

    static String optionalString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            return null;
        }
        return string;
    }

    static Long optionalLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            return null;
        }
        return number.longValue();
    }

    static int optionalInt(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return defaultValue;
    }
}
