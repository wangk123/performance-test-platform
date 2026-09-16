package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 扫描 JMX 脚本中 JSR223 前置/后置处理器 script 内疑似明文密钥的写法，
 * 输出不阻断保存的 warning 文案，引导改用 ${__P(...)} 参数化引用。
 */
@Component
public class Jsr223SecretScanner {
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(secret|password|passwd|token|appkey|api[-_]?key|salt|private[-_]?key)\\s*=\\s*['\"]([A-Za-z0-9+/=_-]{16,})['\"]");

    private final JmeterScriptParser parser;

    public Jsr223SecretScanner(JmeterScriptParser parser) {
        this.parser = parser;
    }

    public List<String> scanScriptContent(String jmxContent) {
        List<ScriptStepDefinition> steps;
        try {
            steps = parser.parseSteps(jmxContent);
        } catch (Exception exception) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        collect(steps, warnings);
        return warnings;
    }

    private void collect(List<ScriptStepDefinition> steps, List<String> warnings) {
        for (ScriptStepDefinition step : steps) {
            if (isJsr223(step.type())) {
                scanStep(step, warnings);
            }
            collect(step.children(), warnings);
        }
    }

    private void scanStep(ScriptStepDefinition step, List<String> warnings) {
        Object script = step.config().get("script");
        if (!(script instanceof String text) || text.isEmpty()) {
            return;
        }
        int hits = 0;
        Matcher matcher = SECRET_PATTERN.matcher(text);
        while (matcher.find()) {
            hits++;
            String preview = matcher.group(1) + " = '" + matcher.group(2).substring(0, 6) + "…'";
            warnings.add("步骤 [" + step.name() + "] 第 " + hits + " 处疑似明文密钥（" + preview
                    + "），建议改用 ${__P(...)} 参数化引用");
        }
    }

    private boolean isJsr223(String type) {
        return ScriptStepType.JSR223_PRE_PROCESSOR.code().equals(type)
                || ScriptStepType.JSR223_POST_PROCESSOR.code().equals(type);
    }
}
