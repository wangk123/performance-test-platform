package com.yr.perftest.platform.script;

import java.util.List;

/**
 * 脚本保存结果：版本信息携带不阻断保存的明文密钥扫描 warnings（恒非空数组）。
 */
public record SaveScriptDefinitionResult(ScriptVersion version, List<String> warnings) {
    public SaveScriptDefinitionResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
