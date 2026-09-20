package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterScriptRenderer {
    public String render(List<ScriptStepDefinition> steps) {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.6.3\">\n");
        builder.append("  <hashTree>\n");
        builder.append("    <TestPlan guiclass=\"TestPlanGui\" testclass=\"TestPlan\" testname=\"Test Plan\" enabled=\"true\">\n");
        builder.append("      <stringProp name=\"TestPlan.comments\"></stringProp>\n");
        builder.append("      <boolProp name=\"TestPlan.functional_mode\">false</boolProp>\n");
        builder.append("      <boolProp name=\"TestPlan.serialize_threadgroups\">false</boolProp>\n");
        builder.append("    </TestPlan>\n");
        builder.append("    <hashTree>\n");
        for (ScriptStepDefinition step : steps) {
            if (step.stepType() == ScriptStepType.THREAD_GROUP) {
                appendThreadGroup(builder, step);
            } else {
                appendStep(builder, step);
            }
        }
        builder.append("    </hashTree>\n");
        builder.append("  </hashTree>\n");
        builder.append("</jmeterTestPlan>\n");
        return builder.toString();
    }

    public String renderStepFragment(ScriptStepDefinition step) {
        StringBuilder builder = new StringBuilder();
        appendStep(builder, step);
        return builder.toString();
    }

    private void appendThreadGroup(StringBuilder builder, ScriptStepDefinition step) {
        ThreadGroupConfig tgConfig = step.threadGroupConfig();
        if (ThreadGroupConfig.MODE_STEPPING.equals(tgConfig.mode())) {
            appendSteppingThreadGroup(builder, step, tgConfig);
            return;
        }
        boolean useScheduler = tgConfig.scheduler();
        int loops = useScheduler ? -1 : tgConfig.loops();

        builder.append("      <ThreadGroup guiclass=\"ThreadGroupGui\" testclass=\"ThreadGroup\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("        <stringProp name=\"ThreadGroup.num_threads\">").append(tgConfig.threads()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"ThreadGroup.ramp_time\">").append(tgConfig.rampUp()).append("</stringProp>\n");
        if (useScheduler) {
            builder.append("        <boolProp name=\"ThreadGroup.scheduler\">true</boolProp>\n");
        }
        builder.append("        <stringProp name=\"ThreadGroup.duration\">").append(tgConfig.duration()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"ThreadGroup.delay\">0</stringProp>\n");
        builder.append("        <elementProp name=\"ThreadGroup.main_controller\" elementType=\"LoopController\">\n");
        builder.append("          <boolProp name=\"LoopController.continue_forever\">false</boolProp>\n");
        builder.append("          <stringProp name=\"LoopController.loops\">").append(loops).append("</stringProp>\n");
        builder.append("        </elementProp>\n");
        builder.append("      </ThreadGroup>\n");
        builder.append("      <hashTree>\n");
        appendChildren(builder, step.children());
        builder.append("      </hashTree>\n");
    }

    private void appendSteppingThreadGroup(StringBuilder builder, ScriptStepDefinition step, ThreadGroupConfig config) {
        ThreadGroupConfig.SteppingConfig stepping = config.stepping();
        builder.append("      <kg.apc.jmeter.threads.SteppingThreadGroup guiclass=\"kg.apc.jmeter.threads.SteppingThreadGroupGui\" testclass=\"kg.apc.jmeter.threads.SteppingThreadGroup\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("        <stringProp name=\"ThreadGroup.num_threads\">").append(config.threads()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Threads initial delay\">").append(stepping.initialDelay()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Start users count\">").append(stepping.startUsersCount()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Start users period\">").append(stepping.startUsersPeriod()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Start users count burst\">").append(stepping.burst()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"rampUp\">").append(stepping.rampUp()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"flighttime\">").append(stepping.flightTime()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Stop users count\">").append(stepping.stopUsersCount()).append("</stringProp>\n");
        builder.append("        <stringProp name=\"Stop users period\">").append(stepping.stopUsersPeriod()).append("</stringProp>\n");
        builder.append("      </kg.apc.jmeter.threads.SteppingThreadGroup>\n");
        builder.append("      <hashTree>\n");
        appendChildren(builder, step.children());
        builder.append("      </hashTree>\n");
    }

    private void appendChildren(StringBuilder builder, List<ScriptStepDefinition> steps) {
        for (ScriptStepDefinition step : steps) {
            if (step.stepType() == null) {
                continue;
            }
            appendStep(builder, step);
        }
    }

    private void appendStep(StringBuilder builder, ScriptStepDefinition step) {
        switch (step.stepType()) {
            case HTTP_REQUEST -> appendHttpSampler(builder, step);
            case RESPONSE_ASSERTION -> appendAssertion(builder, step);
            case JSON_ASSERTION -> appendJsonAssertion(builder, step);
            case CSV_DATA -> appendCsv(builder, step);
            case USER_PARAMS -> appendUserParams(builder, step);
            case USER_VARIABLES -> appendUserVariables(builder, step);
            case HEADER_CONFIG -> appendHeaderManager(builder, step);
            case CONSTANT_TIMER -> appendConstantTimer(builder, step);
            case RANDOM_TIMER -> appendRandomTimer(builder, step);
            case JSR223_PRE_PROCESSOR -> appendJsr223(builder, step, "JSR223PreProcessor");
            case JSR223_POST_PROCESSOR -> appendJsr223(builder, step, "JSR223PostProcessor");
            default -> throw new ScriptValidationException("unsupported step type: " + step.type());
        }
    }

    private void appendHttpSampler(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config();
        String url = text(config, "url", "");
        UrlParts urlParts = parseUrl(url);
        String protocol = text(config, "protocol", urlParts.protocol());
        String domain = urlParts.domain().isBlank() ? text(config, "domain", "") : urlParts.domain();
        String port = urlParts.port().isBlank() ? text(config, "port", "") : urlParts.port();
        String path = urlParts.path().isBlank() ? text(config, "path", "/") : urlParts.path();
        String method = text(config, "method", "GET");
        String bodyType = text(config, "bodyType", "none");
        String defaultName = method + " " + path.replaceFirst("\\?.*$", "");
        String testName = step.name();
        if (testName == null || testName.isBlank()) {
            testName = defaultName;
        }
        builder.append("        <HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" testclass=\"HTTPSamplerProxy\" testname=\"")
                .append(xml(testName)).append("\"").append(enabledAttr(step)).append(">\n");
        if ("raw".equals(bodyType)) {
            builder.append("          <boolProp name=\"HTTPSampler.postBodyRaw\">true</boolProp>\n");
        }
        appendHttpArguments(builder, config, bodyType);
        builder.append("          <stringProp name=\"HTTPSampler.protocol\">").append(xml(protocol)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.domain\">").append(xml(domain)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.port\">").append(xml(port)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.path\">").append(xml(path)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.method\">").append(xml(method)).append("</stringProp>\n");
        builder.append("          <boolProp name=\"HTTPSampler.follow_redirects\">").append(bool(config, "advanced", "followRedirects", true)).append("</boolProp>\n");
        builder.append("          <boolProp name=\"HTTPSampler.use_keepalive\">").append(bool(config, "advanced", "keepAlive", true)).append("</boolProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.connect_timeout\">").append(number(config, "advanced", "connectTimeout", 30000)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"HTTPSampler.response_timeout\">").append(number(config, "advanced", "responseTimeout", 30000)).append("</stringProp>\n");
        builder.append("        </HTTPSamplerProxy>\n");
        builder.append("        <hashTree>\n");
        appendHttpHeaders(builder, config);
        appendChildren(builder, step.children());
        builder.append("        </hashTree>\n");
    }

    private void appendAssertion(StringBuilder builder, ScriptStepDefinition step) {
        String target = assertionTarget(text(step.config(), "target", "body"));
        int matchType = assertionMatchType(text(step.config(), "match", "contains"));
        builder.append("          <ResponseAssertion guiclass=\"AssertionGui\" testclass=\"ResponseAssertion\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("            <collectionProp name=\"Assertion.test_strings\"><stringProp name=\"0\">")
                .append(xml(text(step.config(), "rule", ""))).append("</stringProp></collectionProp>\n");
        builder.append("            <stringProp name=\"Assertion.test_field\">").append(target).append("</stringProp>\n");
        builder.append("            <boolProp name=\"Assertion.assume_success\">false</boolProp>\n");
        builder.append("            <intProp name=\"Assertion.test_type\">").append(matchType).append("</intProp>\n");
        builder.append("          </ResponseAssertion>\n");
        builder.append("          <hashTree/>\n");
    }

    private String assertionTarget(String target) {
        return switch (target) {
            case "statusCode", "响应码" -> "Assertion.response_code";
            case "headers", "响应头", "Header" -> "Assertion.response_headers";
            default -> "Assertion.response_data";
        };
    }

    private int assertionMatchType(String match) {
        return switch (match) {
            case "equals" -> 8;
            case "regex" -> 1;
            default -> 2;
        };
    }

    private void appendJsonAssertion(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config();
        boolean validateValue = bool(config, "validateValue", false);
        boolean useRegex = bool(config, "useRegex", false);
        builder.append("          <JSONPathAssertion guiclass=\"JSONPathAssertionGui\" testclass=\"JSONPathAssertion\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("            <stringProp name=\"JSON_PATH\">").append(xml(text(config, "jsonPath", ""))).append("</stringProp>\n");
        builder.append("            <stringProp name=\"EXPECTED_VALUE\">").append(xml(text(config, "expectedValue", ""))).append("</stringProp>\n");
        builder.append("            <boolProp name=\"JSONVALIDATION\">").append(validateValue).append("</boolProp>\n");
        builder.append("            <boolProp name=\"EXPECT_NULL\">false</boolProp>\n");
        builder.append("            <boolProp name=\"INVERT\">false</boolProp>\n");
        builder.append("            <boolProp name=\"ISREGEX\">").append(useRegex).append("</boolProp>\n");
        builder.append("          </JSONPathAssertion>\n");
        builder.append("          <hashTree/>\n");
    }

    /** JMeter 元件启用属性：config.enabled 为 false 时输出 enabled="false"（JMeter 中灰显且不执行），缺省启用。 */
    private String enabledAttr(ScriptStepDefinition step) {
        Object value = step.config() == null ? null : step.config().get("enabled");
        boolean enabled = value == null || Boolean.parseBoolean(String.valueOf(value));
        return " enabled=\"" + enabled + "\"";
    }

    private boolean bool(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private void appendCsv(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config();
        builder.append("        <CSVDataSet guiclass=\"TestBeanGUI\" testclass=\"CSVDataSet\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <stringProp name=\"filename\">").append(xml(text(config, "fileName", ""))).append("</stringProp>\n");
        builder.append("          <stringProp name=\"variableNames\">").append(xml(text(config, "variableNames", ""))).append("</stringProp>\n");
        appendCsvProp(builder, config, "delimiter");
        appendCsvProp(builder, config, "fileEncoding");
        appendCsvProp(builder, config, "ignoreFirstLine");
        appendCsvProp(builder, config, "recycle");
        appendCsvProp(builder, config, "stopThread");
        appendCsvProp(builder, config, "shareMode");
        builder.append("        </CSVDataSet>\n");
        builder.append("        <hashTree/>\n");
    }

    private void appendCsvProp(StringBuilder builder, Map<String, Object> config, String key) {
        String value = text(config, key, "");
        if (value.isBlank()) {
            return;
        }
        builder.append("          <stringProp name=\"").append(key).append("\">").append(xml(value)).append("</stringProp>\n");
    }

    private void appendJsr223(StringBuilder builder, ScriptStepDefinition step, String tag) {
        Map<String, Object> config = step.config() == null ? Map.of() : step.config();
        String lang = text(config, "scriptLanguage", "groovy");
        String params = text(config, "parameters", "");
        String script = text(config, "script", "");
        String cacheKey = String.valueOf(bool(config, "cacheKey", true));
        builder.append("        <").append(tag).append(" guiclass=\"TestBeanGUI\" testclass=\"").append(tag)
                .append("\" testname=\"").append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <stringProp name=\"cacheKey\">").append(xml(cacheKey)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"scriptLanguage\">").append(xml(lang)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"parameters\">").append(xml(params)).append("</stringProp>\n");
        builder.append("          <stringProp name=\"filename\"></stringProp>\n");
        builder.append("          <stringProp name=\"script\">").append(xml(script)).append("</stringProp>\n");
        builder.append("        </").append(tag).append(">\n");
        builder.append("        <hashTree/>\n");
    }

    /** JMeter「用户参数」矩阵：names 一列变量名，thread_values 每个内层 collection 是一个用户的各变量取值。 */
    private void appendUserParams(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config() == null ? Map.of() : step.config();
        List<String> names = strings(config.get("names"));
        List<List<String>> users = userColumns(config.get("users"));
        if (names.isEmpty() && !users.isEmpty()) {
            int width = users.stream().mapToInt(List::size).max().orElse(0);
            for (int index = 0; index < width; index++) {
                names.add("param" + (index + 1));
            }
        }
        builder.append("        <UserParameters guiclass=\"UserParametersGui\" testclass=\"UserParameters\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <collectionProp name=\"UserParameters.names\">\n");
        for (int index = 0; index < names.size(); index++) {
            builder.append("            <stringProp name=\"").append(index).append("\">")
                    .append(xml(names.get(index))).append("</stringProp>\n");
        }
        builder.append("          </collectionProp>\n");
        builder.append("          <collectionProp name=\"UserParameters.thread_values\">\n");
        for (int userIndex = 0; userIndex < users.size(); userIndex++) {
            List<String> values = users.get(userIndex);
            builder.append("            <collectionProp name=\"").append(userIndex).append("\">\n");
            for (int index = 0; index < names.size(); index++) {
                String value = index < values.size() ? values.get(index) : "";
                builder.append("              <stringProp name=\"").append(index).append("\">")
                        .append(xml(value)).append("</stringProp>\n");
            }
            builder.append("            </collectionProp>\n");
        }
        builder.append("          </collectionProp>\n");
        builder.append("          <boolProp name=\"UserParameters.per_iteration\">")
                .append(bool(config, "perIteration", false)).append("</boolProp>\n");
        builder.append("        </UserParameters>\n");
        builder.append("        <hashTree/>\n");
    }

    /** JMeter「用户定义的变量」（Arguments 元件）：键值对集合。 */
    private void appendUserVariables(StringBuilder builder, ScriptStepDefinition step) {
        List<Map<String, Object>> variables = params(step.config() == null ? null : step.config().get("variables"));
        builder.append("        <Arguments guiclass=\"ArgumentsPanel\" testclass=\"Arguments\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <collectionProp name=\"Arguments.arguments\">\n");
        for (Map<String, Object> variable : variables) {
            String key = text(variable, "key", "");
            builder.append("            <elementProp name=\"").append(xml(key)).append("\" elementType=\"Argument\">\n");
            builder.append("              <stringProp name=\"Argument.name\">").append(xml(key)).append("</stringProp>\n");
            builder.append("              <stringProp name=\"Argument.value\">").append(xml(text(variable, "value", ""))).append("</stringProp>\n");
            String description = text(variable, "description", "");
            if (!description.isEmpty()) {
                builder.append("              <stringProp name=\"Argument.desc\">").append(xml(description)).append("</stringProp>\n");
            }
            builder.append("              <stringProp name=\"Argument.metadata\">=</stringProp>\n");
            builder.append("            </elementProp>\n");
        }
        builder.append("          </collectionProp>\n");
        builder.append("        </Arguments>\n");
        builder.append("        <hashTree/>\n");
    }

    private void appendConstantTimer(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config() == null ? Map.of() : step.config();
        builder.append("        <ConstantTimer guiclass=\"ConstantTimerGui\" testclass=\"ConstantTimer\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <stringProp name=\"ConstantTimer.delay\">")
                .append(xml(text(config, "delay", "300"))).append("</stringProp>\n");
        builder.append("        </ConstantTimer>\n");
        builder.append("        <hashTree/>\n");
    }

    private void appendHeaderManager(StringBuilder builder, ScriptStepDefinition step) {
        List<Map<String, Object>> headers = headerItems(step.config());
        builder.append("        <HeaderManager guiclass=\"HeaderPanel\" testclass=\"HeaderManager\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <collectionProp name=\"HeaderManager.headers\">\n");
        for (Map<String, Object> header : headers) {
            String key = text(header, "key", "");
            builder.append("            <elementProp name=\"").append(xml(key)).append("\" elementType=\"Header\">\n");
            builder.append("              <stringProp name=\"Header.name\">").append(xml(key)).append("</stringProp>\n");
            builder.append("              <stringProp name=\"Header.value\">").append(xml(text(header, "value", ""))).append("</stringProp>\n");
            builder.append("            </elementProp>\n");
        }
        builder.append("          </collectionProp>\n");
        builder.append("        </HeaderManager>\n");
        builder.append("        <hashTree/>\n");
    }

    /** headers 结构化数组优先；旧数据只有 headersText（k: v / k=v 每行一条）时解析兜底。 */
    private List<Map<String, Object>> headerItems(Map<String, Object> config) {
        List<Map<String, Object>> headers = params(config == null ? null : config.get("headers"));
        if (!headers.isEmpty() || config == null) {
            return headers;
        }
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (String line : text(config, "headersText", "").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 0) {
                separator = trimmed.indexOf('=');
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enabled", true);
            item.put("key", separator > 0 ? trimmed.substring(0, separator).trim() : trimmed);
            item.put("value", separator > 0 ? trimmed.substring(separator + 1).trim() : "");
            item.put("description", "");
            parsed.add(item);
        }
        return parsed;
    }

    private List<String> strings(Object source) {
        if (!(source instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> userColumns(Object source) {
        if (!(source instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(List.class::isInstance)
                .map(column -> ((List<Object>) column).stream().map(String::valueOf).toList())
                .toList();
    }

    private void appendRandomTimer(StringBuilder builder, ScriptStepDefinition step) {
        Map<String, Object> config = step.config() == null ? Map.of() : step.config();
        builder.append("        <UniformRandomTimer guiclass=\"UniformRandomTimerGui\" testclass=\"UniformRandomTimer\" testname=\"")
                .append(xml(step.name())).append("\"").append(enabledAttr(step)).append(">\n");
        builder.append("          <stringProp name=\"UniformRandomTimer.delay\">")
                .append(xml(text(config, "delay", "0"))).append("</stringProp>\n");
        builder.append("          <stringProp name=\"UniformRandomTimer.range\">")
                .append(xml(text(config, "range", "0"))).append("</stringProp>\n");
        builder.append("        </UniformRandomTimer>\n");
        builder.append("        <hashTree/>\n");
    }

    private int number(Map<String, Object> config, String key, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(config.getOrDefault(key, fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int number(Map<String, Object> config, String parentKey, String key, int fallback) {
        Object parent = config.get(parentKey);
        if (parent instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value != null) {
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException exception) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    private boolean bool(Map<String, Object> config, String parentKey, String key, boolean fallback) {
        Object parent = config.get(parentKey);
        if (parent instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
        }
        return fallback;
    }

    private String text(Map<String, Object> config, String key, String fallback) {
        Object value = config.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private UrlParts parseUrl(String url) {
        if (url == null || url.isBlank()) {
            return new UrlParts("", "", "", "");
        }
        String source = url.trim();
        String protocol = "";
        String rest = source;
        int protocolIndex = source.indexOf("://");
        if (protocolIndex >= 0) {
            protocol = source.substring(0, protocolIndex);
            rest = source.substring(protocolIndex + 3);
        }
        int pathIndex = rest.indexOf('/');
        String authority = pathIndex >= 0 ? rest.substring(0, pathIndex) : rest;
        String path = pathIndex >= 0 ? rest.substring(pathIndex) : "/";
        if (authority.isBlank()) {
            return new UrlParts(protocol, "", "", path);
        }
        int portIndex = authority.lastIndexOf(':');
        if (portIndex > 0 && portIndex < authority.length() - 1) {
            String port = authority.substring(portIndex + 1);
            if (port.chars().allMatch(Character::isDigit)) {
                return new UrlParts(protocol, authority.substring(0, portIndex), port, path);
            }
        }
        return new UrlParts(protocol, authority, "", path);
    }

    private void appendHttpArguments(StringBuilder builder, Map<String, Object> config, String bodyType) {
        builder.append("          <elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\">\n");
        builder.append("            <collectionProp name=\"Arguments.arguments\">\n");
        if ("raw".equals(bodyType)) {
            appendHttpArgument(builder, "", text(config, "body", ""));
        } else if ("form-urlencoded".equals(bodyType) || "form-data".equals(bodyType)) {
            appendParamArguments(builder, config.get("bodyParams"));
        } else {
            appendParamArguments(builder, config.get("params"));
        }
        builder.append("            </collectionProp>\n");
        builder.append("          </elementProp>\n");
    }

    private void appendHttpHeaders(StringBuilder builder, Map<String, Object> config) {
        List<Map<String, Object>> headers = params(config.get("headers"));
        if (headers.isEmpty()) {
            return;
        }
        builder.append("          <HeaderManager guiclass=\"HeaderPanel\" testclass=\"HeaderManager\" testname=\"HTTP Header Manager\" enabled=\"true\">\n");
        builder.append("            <collectionProp name=\"HeaderManager.headers\">\n");
        for (Map<String, Object> header : headers) {
            builder.append("              <elementProp name=\"").append(xml(text(header, "key", ""))).append("\" elementType=\"Header\">\n");
            builder.append("                <stringProp name=\"Header.name\">").append(xml(text(header, "key", ""))).append("</stringProp>\n");
            builder.append("                <stringProp name=\"Header.value\">").append(xml(text(header, "value", ""))).append("</stringProp>\n");
            builder.append("              </elementProp>\n");
        }
        builder.append("            </collectionProp>\n");
        builder.append("          </HeaderManager>\n");
        builder.append("          <hashTree/>\n");
    }

    private void appendParamArguments(StringBuilder builder, Object source) {
        for (Map<String, Object> param : params(source)) {
            appendHttpArgument(builder, text(param, "key", ""), text(param, "value", ""));
        }
    }

    private void appendHttpArgument(StringBuilder builder, String key, String value) {
        builder.append("              <elementProp name=\"").append(xml(key)).append("\" elementType=\"HTTPArgument\">\n");
        builder.append("                <boolProp name=\"HTTPArgument.always_encode\">false</boolProp>\n");
        builder.append("                <stringProp name=\"Argument.name\">").append(xml(key)).append("</stringProp>\n");
        builder.append("                <stringProp name=\"Argument.value\">").append(xml(value)).append("</stringProp>\n");
        builder.append("                <stringProp name=\"Argument.metadata\">=</stringProp>\n");
        builder.append("                <boolProp name=\"HTTPArgument.use_equals\">true</boolProp>\n");
        builder.append("              </elementProp>\n");
    }

    private List<Map<String, Object>> params(Object source) {
        if (!(source instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String xml(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record UrlParts(String protocol, String domain, String port, String path) {
    }
}
