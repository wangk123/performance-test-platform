package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * V12 存量回填的纯计算部分：为每条无脚本归属的旧版本记录规划一个 scripts 壳
 * （名称 = 文件名去 .jmx 后缀小写；同项目重名追加 -{版本行 id} 去重；截断到列宽 200）。
 */
@Component
public class ScriptBackfillCalculator {
    private static final int NAME_MAX_LENGTH = 200;

    public record LegacyVersionRow(
            long id, long projectId, String originalFilename, int versionNo, String uploadedBy, Instant uploadedAt) {
    }

    public record BackfillPlan(
            String scriptName, int latestVersionNo, String createdBy, Instant createdAt, long versionId) {
    }

    public List<BackfillPlan> backfillPlans(List<LegacyVersionRow> rows) {
        Set<String> usedNames = new HashSet<>();
        List<BackfillPlan> plans = new ArrayList<>();
        for (LegacyVersionRow row : rows) {
            String base = row.originalFilename()
                    .replaceFirst("(?i)\\.jmx$", "")
                    .toLowerCase(Locale.ROOT);
            String name = fitName(base, row.id(), usedNames);
            usedNames.add(name);
            plans.add(new BackfillPlan(name, row.versionNo(), row.uploadedBy(), row.uploadedAt(), row.id()));
        }
        return plans;
    }

    private String fitName(String base, long rowId, Set<String> usedNames) {
        String candidate = truncate(base, NAME_MAX_LENGTH);
        if (usedNames.contains(candidate)) {
            String suffix = "-" + rowId;
            candidate = truncate(base, NAME_MAX_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
