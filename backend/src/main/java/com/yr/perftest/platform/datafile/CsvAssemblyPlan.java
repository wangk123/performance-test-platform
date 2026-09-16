package com.yr.perftest.platform.datafile;

import java.nio.file.Path;

/**
 * 单个 CSV 步骤的执行装配计划：脚本步骤 → 数据文件版本 → 执行目录目标文件。
 */
public record CsvAssemblyPlan(
        String stepId,
        String stepName,
        long dataFileId,
        int versionNo,
        String sha256,
        String targetFileName,
        Path sourcePath
) {
}
