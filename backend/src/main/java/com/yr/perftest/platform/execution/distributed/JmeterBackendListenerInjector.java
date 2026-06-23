package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.script.ScriptValidationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Path;
import java.util.Map;

@Component
public class JmeterBackendListenerInjector {
    public void inject(Path source, Path target, String runId, String influxdbUrl, String measurement) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(source.toFile());
            Element testPlan = (Element) document.getElementsByTagName("TestPlan").item(0);
            Element hashTree = nextHashTree(testPlan);
            if (hashTree == null) {
                throw new ScriptValidationException("script content is not a JMeter test plan");
            }
            removeBackendListeners(hashTree);
            hashTree.appendChild(backendListener(document, runId, influxdbUrl, measurement));
            hashTree.appendChild(document.createElement("hashTree"));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(target.toFile()));
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to inject BackendListener");
        }
    }

    private void removeBackendListeners(Element hashTree) {
        for (Node node = hashTree.getFirstChild(); node != null; ) {
            Node next = node.getNextSibling();
            if (node instanceof Element element && "BackendListener".equals(element.getTagName())) {
                hashTree.removeChild(element);
                if (next instanceof Element nextElement && "hashTree".equals(nextElement.getTagName())) {
                    hashTree.removeChild(nextElement);
                    next = nextElement.getNextSibling();
                }
            }
            node = next;
        }
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
        listener.setAttribute("testname", "InfluxDB Backend Listener");
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
                "percentiles", "90;95;99",
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

    private Element stringProp(Document document, String name, String value) {
        Element element = document.createElement("stringProp");
        element.setAttribute("name", name);
        element.setTextContent(value);
        return element;
    }
}
