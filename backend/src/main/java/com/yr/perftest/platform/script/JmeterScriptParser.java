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
            for (Element threadGroup : elements(document, "ThreadGroup")) {
                steps.add(parseThreadGroup(threadGroup));
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
        List<ScriptStepDefinition> children = parseChildren(nextHashTree(threadGroup));
        return new ScriptStepDefinition(
                "thread-" + Math.abs(System.identityHashCode(threadGroup)),
                "THREAD_GROUP",
                threadGroup.getAttribute("testname"),
                config,
                children
        );
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
            switch (element.getTagName()) {
                case "HTTPSamplerProxy" -> steps.add(parseHttpSampler(element, nextHashTree(element)));
                case "CSVDataSet" -> steps.add(parseCsv(element));
                case "Arguments" -> steps.add(parseUserParams(element));
                case "HeaderManager" -> steps.add(parseHeaderManager(element));
                case "ResponseAssertion" -> steps.add(parseAssertion(element));
                default -> {
                }
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
        return new ScriptStepDefinition(
                "http-" + Math.abs(System.identityHashCode(sampler)),
                "HTTP_REQUEST",
                method + " " + cleanPath,
                config,
                parseSamplerChildren(hashTree)
        );
    }

    private ScriptStepDefinition parseCsv(Element element) {
        return new ScriptStepDefinition(
                "csv-" + Math.abs(System.identityHashCode(element)),
                "CSV_DATA",
                element.getAttribute("testname"),
                Map.of(
                        "fileName", stringValue(element, "filename", ""),
                        "variableNames", stringValue(element, "variableNames", "")
                ),
                List.of()
        );
    }

    private ScriptStepDefinition parseUserParams(Element element) {
        return new ScriptStepDefinition(
                "vars-" + Math.abs(System.identityHashCode(element)),
                "USER_PARAMS",
                element.getAttribute("testname"),
                Map.of("paramsText", ""),
                List.of()
        );
    }

    private ScriptStepDefinition parseHeaderManager(Element element) {
        return new ScriptStepDefinition(
                "header-" + Math.abs(System.identityHashCode(element)),
                "HEADER_CONFIG",
                element.getAttribute("testname"),
                Map.of("headersText", textLines(parseHeaders(element), ": ")),
                List.of()
        );
    }

    private ScriptStepDefinition parseAssertion(Element element) {
        return new ScriptStepDefinition(
                "assert-" + Math.abs(System.identityHashCode(element)),
                "ASSERTION",
                element.getAttribute("testname"),
                Map.of("target", "响应体", "rule", stringValue(element, "Assertion.test_strings", "")),
                List.of()
        );
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

    private int intValue(Element root, String name, int fallback) {
        try {
            String value = stringValue(root, name, String.valueOf(fallback));
            return value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private List<ScriptStepDefinition> parseSamplerChildren(Element hashTree) {
        return parseChildren(hashTree).stream()
                .filter(step -> !"HEADER_CONFIG".equals(step.type()))
                .toList();
    }

    private List<Map<String, Object>> parseHeaders(Element root) {
        if (root == null) {
            return List.of();
        }
        List<Map<String, Object>> headers = new ArrayList<>();
        for (int index = 0; index < root.getElementsByTagName("elementProp").getLength(); index++) {
            Element element = (Element) root.getElementsByTagName("elementProp").item(index);
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
