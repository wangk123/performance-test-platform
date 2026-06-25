package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.script.JmeterScriptNormalizer;
import com.yr.perftest.platform.script.ScriptValidationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JmeterBackendListenerInjector {
    private static final String BACKEND_LISTENER_NAME = "InfluxDB Backend Listener";
    private static final String FAILURE_COLLECTOR_NAME = "Failure Collector";
    private static final String FAILURE_QUOTA_NAME = "Failure Detail Quota";

    private final JmeterScriptNormalizer normalizer;

    public JmeterBackendListenerInjector(JmeterScriptNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public void inject(
            Path source,
            Path target,
            String runId,
            String influxdbUrl,
            String measurement,
            Path failureResultPath
    ) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(source.toFile());
            normalizer.normalize(document);
            Element testPlan = (Element) document.getElementsByTagName("TestPlan").item(0);
            Element hashTree = nextHashTree(testPlan);
            if (hashTree == null) {
                throw new ScriptValidationException("script content is not a JMeter test plan");
            }
            removeInjectedElements(hashTree);
            hashTree.appendChild(backendListener(document, runId, influxdbUrl, measurement));
            hashTree.appendChild(document.createElement("hashTree"));
            hashTree.appendChild(failureCollector(document, failureResultPath));
            hashTree.appendChild(document.createElement("hashTree"));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(target.toFile()));
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to inject JMeter runtime listeners");
        }
    }

    private void injectFailureQuotaProcessors(Document document) {
        NodeList threadGroups = document.getElementsByTagName("ThreadGroup");
        for (int index = 0; index < threadGroups.getLength(); index++) {
            Element threadGroup = (Element) threadGroups.item(index);
            Element threadHashTree = nextHashTree(threadGroup);
            if (threadHashTree == null) {
                continue;
            }
            removeFailureQuotaProcessors(threadHashTree);
            threadHashTree.appendChild(failureDetailQuotaProcessor(document));
            threadHashTree.appendChild(document.createElement("hashTree"));
        }
    }

    private void removeInjectedElements(Element hashTree) {
        for (Node node = hashTree.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node instanceof Element element && shouldRemove(element)) {
                hashTree.removeChild(element);
                if (next instanceof Element nextElement && "hashTree".equals(nextElement.getTagName())) {
                    hashTree.removeChild(nextElement);
                    next = nextElement.getNextSibling();
                }
            }
            node = next;
        }
    }

    private void removeFailureQuotaProcessors(Element hashTree) {
        for (Node node = hashTree.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node instanceof Element element
                    && "JSR223PostProcessor".equals(element.getTagName())
                    && FAILURE_QUOTA_NAME.equals(element.getAttribute("testname"))) {
                hashTree.removeChild(element);
                if (next instanceof Element nextElement && "hashTree".equals(nextElement.getTagName())) {
                    hashTree.removeChild(nextElement);
                    next = nextElement.getNextSibling();
                }
            }
            node = next;
        }
    }

    private boolean shouldRemove(Element element) {
        String tagName = element.getTagName();
        String testName = element.getAttribute("testname");
        return ("BackendListener".equals(tagName) && BACKEND_LISTENER_NAME.equals(testName))
                || ("ResultCollector".equals(tagName) && FAILURE_COLLECTOR_NAME.equals(testName));
    }

    private Element failureCollector(Document document, Path failureResultPath) {
        Element collector = document.createElement("ResultCollector");
        collector.setAttribute("guiclass", "SimpleDataWriter");
        collector.setAttribute("testclass", "ResultCollector");
        collector.setAttribute("testname", FAILURE_COLLECTOR_NAME);
        collector.setAttribute("enabled", "true");
        collector.appendChild(boolProp(document, "ResultCollector.error_logging", true));
        collector.appendChild(saveConfig(document));
        collector.appendChild(stringProp(document, "filename", failureResultPath.toString()));
        return collector;
    }

    private Element saveConfig(Document document) {
        Element objProp = document.createElement("objProp");
        Element nameElement = document.createElement("name");
        nameElement.setTextContent("saveConfig");
        objProp.appendChild(nameElement);
        Element value = document.createElement("value");
        value.setAttribute("class", "SampleSaveConfiguration");
        Map<String, Boolean> fields = new LinkedHashMap<>();
        fields.put("time", true);
        fields.put("latency", true);
        fields.put("timestamp", true);
        fields.put("success", true);
        fields.put("label", true);
        fields.put("code", true);
        fields.put("message", true);
        fields.put("threadName", true);
        fields.put("dataType", true);
        fields.put("assertions", true);
        fields.put("responseData", true);
        fields.put("samplerData", true);
        fields.put("fieldNames", true);
        fields.put("responseHeaders", true);
        fields.put("requestHeaders", true);
        fields.put("responseDataOnError", true);
        fields.put("saveAssertionResultsFailureMessage", true);
        fields.put("bytes", true);
        fields.put("sentBytes", true);
        fields.put("url", true);
        fields.put("threadCounts", true);
        fields.put("idleTime", true);
        fields.put("connectTime", true);
        fields.put("xml", true);
        fields.forEach((fieldName, enabled) -> {
            Element field = document.createElement(fieldName);
            field.setTextContent(Boolean.toString(enabled));
            value.appendChild(field);
        });
        objProp.appendChild(value);
        return objProp;
    }

    private Element failureDetailQuotaProcessor(Document document) {
        Element processor = document.createElement("JSR223PostProcessor");
        processor.setAttribute("guiclass", "TestBeanGUI");
        processor.setAttribute("testclass", "JSR223PostProcessor");
        processor.setAttribute("testname", FAILURE_QUOTA_NAME);
        processor.setAttribute("enabled", "true");
        processor.appendChild(stringProp(document, "scriptLanguage", "groovy"));
        processor.appendChild(stringProp(document, "script", failureDetailQuotaScript()));
        processor.appendChild(stringProp(document, "parameters", ""));
        processor.appendChild(stringProp(document, "filename", ""));
        processor.appendChild(stringProp(document, "cacheKey", "true"));
        return processor;
    }

    private String failureDetailQuotaScript() {
        return """
                import java.util.concurrent.ConcurrentHashMap
                import java.util.concurrent.atomic.AtomicInteger
                if (prev.isSuccessful()) return
                def map = props.get('failureDetailQuota')
                if (map == null) {
                  synchronized (props) {
                    map = props.get('failureDetailQuota')
                    if (map == null) {
                      map = new ConcurrentHashMap()
                      props.put('failureDetailQuota', map)
                    }
                  }
                }
                def limit = (props.get('failureDetailLimitPerLabel') ?: '10') as Integer
                def counter = map.computeIfAbsent(prev.getSampleLabel(), { new AtomicInteger() })
                if (counter.incrementAndGet() > limit) {
                  prev.setSamplerData('')
                  prev.setRequestHeaders('')
                  prev.setResponseData(new byte[0], null)
                  prev.setResponseHeaders('')
                }
                """;
    }

    private Element nextHashTree(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return "hashTree".equals(sibling.getTagName()) ? sibling : null;
            }
        }
        return null;
    }

    private Element backendListener(Document document, String runId, String influxdbUrl, String measurement) {
        Element listener = document.createElement("BackendListener");
        listener.setAttribute("guiclass", "BackendListenerGui");
        listener.setAttribute("testclass", "BackendListener");
        listener.setAttribute("testname", BACKEND_LISTENER_NAME);
        listener.setAttribute("enabled", "true");
        listener.appendChild(arguments(document, runId, influxdbUrl, measurement));
        listener.appendChild(stringProp(document, "classname", "org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient"));
        return listener;
    }

    private Element arguments(Document document, String runId, String influxdbUrl, String measurement) {
        Element element = document.createElement("elementProp");
        element.setAttribute("name", "arguments");
        element.setAttribute("elementType", "Arguments");
        element.setAttribute("guiclass", "ArgumentsPanel");
        element.setAttribute("testclass", "Arguments");
        element.setAttribute("enabled", "true");
        Element collection = document.createElement("collectionProp");
        collection.setAttribute("name", "Arguments.arguments");
        Map.of(
                "influxdbMetricsSender", "org.apache.jmeter.visualizers.backend.influxdb.HttpMetricsSender",
                "influxdbUrl", influxdbUrl,
                "application", runId,
                "measurement", measurement,
                "summaryOnly", "false",
                "samplersRegex", ".*",
                "percentiles", "50;90;95;99",
                "testTitle", runId,
                "eventTags", ""
        ).forEach((key, value) -> collection.appendChild(argument(document, key, value)));
        element.appendChild(collection);
        return element;
    }

    private Element argument(Document document, String name, String value) {
        Element element = document.createElement("elementProp");
        element.setAttribute("name", name);
        element.setAttribute("elementType", "Argument");
        element.appendChild(stringProp(document, "Argument.name", name));
        element.appendChild(stringProp(document, "Argument.value", value));
        element.appendChild(stringProp(document, "Argument.metadata", "="));
        return element;
    }

    private Element boolProp(Document document, String name, boolean value) {
        Element element = document.createElement("boolProp");
        element.setAttribute("name", name);
        element.setTextContent(Boolean.toString(value));
        return element;
    }

    private Element stringProp(Document document, String name, String value) {
        Element element = document.createElement("stringProp");
        element.setAttribute("name", name);
        element.setTextContent(value);
        return element;
    }
}
