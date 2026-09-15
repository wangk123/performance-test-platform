package com.yr.perftest.platform.envcheck;

/**
 * 修复声明（spec §3.2）：backupScript 执行后 stdout 首行为备份标识 backupRef；
 * rollbackScript 中 {backupRef} 占位符替换为备份标识。
 */
public record FixSpec(EnvCheckRisk risk, String backupScript, String applyScript,
                      String rollbackScript, String summary) {
}
