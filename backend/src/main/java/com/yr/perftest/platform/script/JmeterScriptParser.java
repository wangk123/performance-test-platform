package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterScriptParser {
    public List<ScriptStepDefinition> parseSteps(String content) {
        try {
            Document document = parseDocument(content);
            List<ScriptStepDefinition> steps = new ArrayList<>();
            // 根层支持线程组与公共元件（用户参数/Header/定时器等）混排，保持 JMX 文档顺序
            for (Element testPlan : elements(document, "TestPlan")) {
                Element hashTree = nextHashTree(testPlan);
                if (hashTree == null) {
                    continue;
                }
                for (Node node = hashTree.getFirstChild(); node != null; node = node.getNextSibling()) {
                    if (node instanceof Element element) {
                        ScriptStepDefinition step = parseStep(element);
                        if (step != null) {
                            steps.add(step);
                        }
                    }
                }
            }
            if (steps.isEmpty()) {
                // 兜底：非标准结构（缺 TestPlan）时退回全局扫描线程组
                for (Element threadGroup : elements(document, "ThreadGroup")) {
                    steps.add(parseThreadGroup(threadGroup));
                }
                for (Element threadGroup : elements(document, "kg.apc.jmeter.threads.SteppingThreadGroup")) {
                    steps.add(parseSteppingThreadGroup(threadGroup));
                }
            }
            return steps;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to parse JMeter script");
        }
    }

    private ScriptStepDefinition parseThreadGroup(Element threadGroup) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("threads", intValue(threadGroup, "ThreadGroup.num_threads", 1));
        config.put("rampUp", intValue(threadGroup, "ThreadGroup.ramp_time", 0));
        config.put("loops", intValue(threadGroup, "LoopController.loops", 1));
        config.put("duration", intValue(threadGroup, "ThreadGroup.duration", 0));
        config.put("scheduler", boolValue(threadGroup, "ThreadGroup.scheduler", false));
        List<ScriptStepDefinition> children = parseChildren(nextHashTree(threadGroup));
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(threadGroup, ScriptStepType.THREAD_GROUP),
                ScriptStepType.THREAD_GROUP.code(),
                threadGroup.getAttribute("testname"),
                config,
                children
        );
    }

    /** 根层与各层级共用的元件分发：识别返回步骤定义，未知元件返回 null（由调用方保留在 DOM）。 */
    private ScriptStepDefinition parseStep(Element element) {
        return switch (element.getTagName()) {
            case "ThreadGroup" -> parseThreadGroup(element);
            case "kg.apc.jmeter.threads.SteppingThreadGroup" -> parseSteppingThreadGroup(element);
            case "HTTPSamplerProxy" -> parseHttpSampler(element, nextHashTree(element));
            case "CSVDataSet" -> parseCsv(element);
            case "UserParameters" -> parseUserParams(element);
            case "Arguments" -> parseUserVariables(element);
            case "HeaderManager" -> parseHeaderManager(element);
            case "ResponseAssertion" -> parseAssertion(element);
            case "JSONPathAssertion" -> parseJsonAssertion(element);
            case "ConstantTimer" -> parseConstantTimer(element);
            case "UniformRandomTimer" -> parseRandomTimer(element);
            case "JSR223PreProcessor" -> parseJsr223(element, ScriptStepType.JSR223_PRE_PROCESSOR);
            case "JSR223PostProcessor" -> parseJsr223(element, ScriptStepType.JSR223_POST_PROCESSOR);
            default -> null;
        };
    }

    private List<ScriptStepDefinition> parseChildren(Element hashTree) {
        if (hashTree == null) {
            return List.of();
        }
        List<ScriptStepDefinition> steps = new ArrayList<>();
        for (Node node = hashTree.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element element)) {
                continue;
            }
            ScriptStepDefinition step = parseStep(element);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }

    private ScriptStepDefinition parseHttpSampler(Element sampler, Element hashTree) {
        Map<String, Object> config = new LinkedHashMap<>();
        String method = stringValue(sampler, "HTTPSampler.method", "GET");
        String domain = stringValue(sampler, "HTTPSampler.domain", "");
        String port = stringValue(sampler, "HTTPSampler.port", "");
        String protocol = stringValue(sampler, "HTTPSampler.protocol", "");
        String path = stringValue(sampler, "HTTPSampler.path", "");
        boolean postBodyRaw = boolValue(sampler, "HTTPSampler.postBodyRaw", false);
        List<Map<String, Object>> arguments = parseArguments(sampler);
        List<Map<String, Object>> queryParams = parseQueryParams(path);
        String cleanPath = path.replaceFirst("\\?.*$", "");
        String authority = port.isBlank() ? domain : domain + ":" + port;
        String url = (protocol.isBlank() ? "" : protocol + "://") + authority + cleanPath;
        config.put("method", method);
        config.put("domain", domain);
        config.put("path", cleanPath);
        config.put("url", url.isBlank() ? cleanPath : url);
        config.put("bodyType", "none");
        config.put("rawBodyType", "json");
        config.put("body", "");
        config.put("params", queryParams);
        config.put("headers", parseHeaders(hashTree));
        config.put("bodyParams", List.of());
        config.put("advanced", Map.of(
                "connectTimeout", intValue(sampler, "HTTPSampler.connect_timeout", 30000),
                "responseTimeout", intValue(sampler, "HTTPSampler.response_timeout", 30000),
                "followRedirects", boolValue(sampler, "HTTPSampler.follow_redirects", true),
                "keepAlive", boolValue(sampler, "HTTPSampler.use_keepalive", true)
        ));
        if (postBodyRaw) {
            config.put("bodyType", "raw");
            config.put("body", arguments.stream().findFirst().map(item -> String.valueOf(item.get("value"))).orElse(""));
        } else if (isFormMethod(method)) {
            config.put("bodyType", arguments.isEmpty() ? "none" : "form-urlencoded");
            config.put("bodyParams", arguments);
        } else {
            List<Map<String, Object>> params = new ArrayList<>(queryParams);
            params.addAll(arguments);
            config.put("params", params);
        }
        String defaultName = method + " " + cleanPath;
        String name = sampler.getAttribute("testname");
        if (name == null || name.isBlank()) {
            name = defaultName;
        }
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(sampler, ScriptStepType.HTTP_REQUEST),
                ScriptStepType.HTTP_REQUEST.code(),
                name,
                config,
                parseSamplerChildren(hashTree)
        );
    }

    private ScriptStepDefinition parseSteppingThreadGroup(Element threadGroup) {
        Map<String, Object> stepping = new LinkedHashMap<>();
        stepping.put("initialDelay", intValue(threadGroup, "Threads initial delay", 0));
        stepping.put("startUsersCount", intValue(threadGroup, "Start users count", 10));
        stepping.put("startUsersPeriod", intValue(threadGroup, "Start users period", 30));
        stepping.put("rampUp", intValue(threadGroup, "rampUp", 0));
        stepping.put("flightTime", intValue(threadGroup, "flighttime", 60));
        stepping.put("stopUsersCount", intValue(threadGroup, "Stop users count", 10));
        stepping.put("stopUsersPeriod", intValue(threadGroup, "Stop users period", 30));
        stepping.put("burst", boolStringValue(threadGroup, "Start users count burst", false));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("threads", intValue(threadGroup, "ThreadGroup.num_threads", 1));
        config.put("rampUp", 0);
        config.put("loops", 1);
        config.put("duration", intValue(threadGroup, "flighttime", 60));
        config.put("scheduler", false);
        config.put("mode", ThreadGroupConfig.MODE_STEPPING);
        config.put("stepping", stepping);
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(threadGroup, ScriptStepType.THREAD_GROUP),
                ScriptStepType.THREAD_GROUP.code(),
                threadGroup.getAttribute("testname"),
                config,
                parseChildren(nextHashTree(threadGroup))
        );
    }

    private ScriptStepDefinition parseCsv(Element element) {
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.CSV_DATA),
                ScriptStepType.CSV_DATA.code(),
                element.getAttribute("testname"),
                Map.of(
                        "fileName", stringValue(element, "filename", ""),
                        "variableNames", stringValue(element, "variableNames", ""),
                        "delimiter", stringValue(element, "delimiter", ","),
                        "fileEncoding", stringValue(element, "fileEncoding", "UTF-8"),
                        "ignoreFirstLine", boolStringValue(element, "ignoreFirstLine", false),
                        "recycle", boolStringValue(element, "recycle", true),
                        "stopThread", boolStringValue(element, "stopThread", false),
                        "shareMode", stringValue(element, "shareMode", "shareMode.all")
                ),
                List.of()
        );
    }

    private ScriptStepDefinition parseJsr223(Element element, ScriptStepType type) {
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, type),
                type.code(),
                element.getAttribute("testname"),
                Map.of(
                        "scriptLanguage", stringValue(element, "scriptLanguage", "groovy"),
                        "script", stringValue(element, "script", ""),
                        "parameters", stringValue(element, "parameters", ""),
                        "cacheKey", boolStringValue(element, "cacheKey", true)
                ),
                List.of()
        );
    }

    /** JMeter「用户参数」（UserParameters）：行=变量名，每列一个用户的取值（thread_values），另带 per_iteration。 */
    private ScriptStepDefinition parseUserParams(Element element) {
        List<String> names = new ArrayList<>();
        Element namesProp = directCollectionProp(element, "UserParameters.names");
        if (namesProp != null) {
            for (Element stringProp : childElementsNamed(namesProp, "stringProp")) {
                names.add(stringProp.getTextContent());
            }
        }
        List<List<String>> users = new ArrayList<>();
        Element valuesProp = directCollectionProp(element, "UserParameters.thread_values");
        if (valuesProp != null) {
            for (Element userCollection : childElementsNamed(valuesProp, "collectionProp")) {
                List<String> values = new ArrayList<>();
                for (Element stringProp : childElementsNamed(userCollection, "stringProp")) {
                    values.add(stringProp.getTextContent());
                }
                users.add(values);
            }
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("names", names);
        config.put("users", users);
        config.put("perIteration", boolValue(element, "UserParameters.per_iteration", false));
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.USER_PARAMS),
                ScriptStepType.USER_PARAMS.code(),
                element.getAttribute("testname"),
                config,
                List.of()
        );
    }

    /** JMeter「用户定义的变量」（Arguments）：键值对集合。 */
    private ScriptStepDefinition parseUserVariables(Element element) {
        List<Map<String, Object>> variables = new ArrayList<>();
        Element arguments = directCollectionProp(element, "Arguments.arguments");
        if (arguments != null) {
            for (Element argument : childElementsNamed(arguments, "elementProp")) {
                Map<String, Object> item = param(
                        stringValue(argument, "Argument.name", ""),
                        stringValue(argument, "Argument.value", ""));
                item.put("description", stringValue(argument, "Argument.desc", ""));
                variables.add(item);
            }
        }
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.USER_VARIABLES),
                ScriptStepType.USER_VARIABLES.code(),
                element.getAttribute("testname"),
                Map.of("variables", variables),
                List.of()
        );
    }

    private ScriptStepDefinition parseConstantTimer(Element element) {
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.CONSTANT_TIMER),
                ScriptStepType.CONSTANT_TIMER.code(),
                element.getAttribute("testname"),
                Map.of("delay", stringValue(element, "ConstantTimer.delay", "300")),
                List.of()
        );
    }

    private ScriptStepDefinition parseRandomTimer(Element element) {
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.RANDOM_TIMER),
                ScriptStepType.RANDOM_TIMER.code(),
                element.getAttribute("testname"),
                Map.of(
                        "delay", stringValue(element, "UniformRandomTimer.delay", "1000"),
                        "range", stringValue(element, "UniformRandomTimer.range", "0")
                ),
                List.of()
        );
    }

    private ScriptStepDefinition parseHeaderManager(Element element) {
        List<Map<String, Object>> headers = parseHeaderItems(element);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("headers", headers);
        config.put("headersText", textLines(headers, ": "));
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.HEADER_CONFIG),
                ScriptStepType.HEADER_CONFIG.code(),
                element.getAttribute("testname"),
                config,
                List.of()
        );
    }

    private ScriptStepDefinition parseAssertion(Element element) {
        String field = stringValue(element, "Assertion.test_field", "Assertion.response_data");
        int matchType = intPropValue(element, "Assertion.test_type", 2);
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.RESPONSE_ASSERTION),
                ScriptStepType.RESPONSE_ASSERTION.code(),
                element.getAttribute("testname"),
                Map.of("target", assertionTarget(field), "match", assertionMatch(matchType), "rule", assertionRule(element)),
                List.of()
        );
    }

    private String assertionTarget(String field) {
        return switch (field) {
            case "Assertion.response_code" -> "statusCode";
            case "Assertion.response_headers" -> "headers";
            default -> "body";
        };
    }

    private String assertionMatch(int matchType) {
        return switch (matchType) {
            case 8 -> "equals";
            case 1 -> "regex";
            default -> "contains";
        };
    }

    private ScriptStepDefinition parseJsonAssertion(Element element) {
        return new ScriptStepDefinition(
                JmeterScriptDom.stepId(element, ScriptStepType.JSON_ASSERTION),
                ScriptStepType.JSON_ASSERTION.code(),
                element.getAttribute("testname"),
                Map.of(
                        "jsonPath", stringValue(element, "JSON_PATH", ""),
                        "validateValue", boolValue(element, "JSONVALIDATION", false),
                        "expectedValue", stringValue(element, "EXPECTED_VALUE", ""),
                        "useRegex", boolValue(element, "ISREGEX", false)
                ),
                List.of()
        );
    }

    private String assertionRule(Element element) {
        for (int index = 0; index < element.getElementsByTagName("collectionProp").getLength(); index++) {
            Element collection = (Element) element.getElementsByTagName("collectionProp").item(index);
            if (!"Assertion.test_strings".equals(collection.getAttribute("name"))) {
                continue;
            }
            for (int itemIndex = 0; itemIndex < collection.getElementsByTagName("stringProp").getLength(); itemIndex++) {
                Element stringProp = (Element) collection.getElementsByTagName("stringProp").item(itemIndex);
                return stringProp.getTextContent();
            }
        }
        return "";
    }

    private Document parseDocument(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    private List<Element> elements(Document document, String tagName) {
        List<Element> items = new ArrayList<>();
        for (int index = 0; index < document.getElementsByTagName(tagName).getLength(); index++) {
            items.add((Element) document.getElementsByTagName(tagName).item(index));
        }
        return items;
    }

    private Element nextHashTree(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return "hashTree".equals(sibling.getTagName()) ? sibling : null;
            }
        }
        return null;
    }

    private Element directCollectionProp(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                    && "collectionProp".equals(element.getTagName())
                    && name.equals(element.getAttribute("name"))) {
                return element;
            }
        }
        return null;
    }

    private List<Element> childElementsNamed(Element parent, String tagName) {
        List<Element> items = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                items.add(element);
            }
        }
        return items;
    }

    private String stringValue(Element root, String name, String fallback) {
        for (int index = 0; index < root.getElementsByTagName("stringProp").getLength(); index++) {
            Element element = (Element) root.getElementsByTagName("stringProp").item(index);
            if (name.equals(element.getAttribute("name"))) {
                return element.getTextContent();
            }
        }
        return fallback;
    }

    private boolean boolValue(Element root, String name, boolean fallback) {
        for (int index = 0; index < root.getElementsByTagName("boolProp").getLength(); index++) {
            Element element = (Element) root.getElementsByTagName("boolProp").item(index);
            if (name.equals(element.getAttribute("name"))) {
                return Boolean.parseBoolean(element.getTextContent());
            }
        }
        return fallback;
    }

    private boolean boolStringValue(Element root, String name, boolean fallback) {
        String value = stringValue(root, name, String.valueOf(fallback));
        return value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private int intValue(Element root, String name, int fallback) {
        try {
            String value = stringValue(root, name, String.valueOf(fallback));
            return value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int intPropValue(Element root, String name, int fallback) {
        try {
            for (int index = 0; index < root.getElementsByTagName("intProp").getLength(); index++) {
                Element element = (Element) root.getElementsByTagName("intProp").item(index);
                if (name.equals(element.getAttribute("name"))) {
                    String value = element.getTextContent();
                    return value.isBlank() ? fallback : Integer.parseInt(value);
                }
            }
            return fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private List<ScriptStepDefinition> parseSamplerChildren(Element hashTree) {
        return parseChildren(hashTree).stream()
                .filter(step -> !ScriptStepType.HEADER_CONFIG.code().equals(step.type()))
                .toList();
    }

    private List<Map<String, Object>> parseHeaders(Element root) {
        if (root == null) {
            return List.of();
        }
        if ("HeaderManager".equals(root.getTagName())) {
            return parseHeaderItems(root);
        }
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && "HeaderManager".equals(element.getTagName())) {
                return parseHeaderItems(element);
            }
        }
        return List.of();
    }

    private List<Map<String, Object>> parseHeaderItems(Element headerManager) {
        List<Map<String, Object>> headers = new ArrayList<>();
        for (int index = 0; index < headerManager.getElementsByTagName("elementProp").getLength(); index++) {
            Element element = (Element) headerManager.getElementsByTagName("elementProp").item(index);
            if ("Header".equals(element.getAttribute("elementType"))) {
                headers.add(param(stringValue(element, "Header.name", ""), stringValue(element, "Header.value", "")));
            }
        }
        return headers;
    }

    private List<Map<String, Object>> parseArguments(Element sampler) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (int index = 0; index < sampler.getElementsByTagName("elementProp").getLength(); index++) {
            Element element = (Element) sampler.getElementsByTagName("elementProp").item(index);
            if ("HTTPArgument".equals(element.getAttribute("elementType"))) {
                params.add(param(stringValue(element, "Argument.name", ""), stringValue(element, "Argument.value", "")));
            }
        }
        return params;
    }

    private List<Map<String, Object>> parseQueryParams(String path) {
        int queryIndex = path.indexOf('?');
        if (queryIndex < 0 || queryIndex == path.length() - 1) {
            return List.of();
        }
        List<Map<String, Object>> params = new ArrayList<>();
        for (String pair : path.substring(queryIndex + 1).split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            params.add(param(decode(key), decode(value)));
        }
        return params;
    }

    private Map<String, Object> param(String key, String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("enabled", true);
        item.put("key", key);
        item.put("value", value);
        item.put("description", "");
        return item;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isFormMethod(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    private String textLines(List<Map<String, Object>> items, String separator) {
        return items.stream()
                .map(item -> String.valueOf(item.get("key")) + separator + String.valueOf(item.get("value")))
                .toList()
                .stream()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
