package com.yr.perftest.platform.script;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class JmeterScriptNormalizer {
    public void normalizeToPath(Path source, Path target) {
        try {
            Document document = parse(source);
            normalize(document);
            write(document, target);
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to normalize JMeter script");
        }
    }

    public String normalizeContent(String content) {
        try {
            Document document = parse(content);
            normalize(document);
            return serialize(document);
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to normalize JMeter script");
        }
    }

    public void normalize(Document document) {
        for (int index = 0; index < document.getElementsByTagName("TestPlan").getLength(); index++) {
            Element testPlan = (Element) document.getElementsByTagName("TestPlan").item(index);
            ensureAttribute(testPlan, "guiclass", "TestPlanGui");
            ensureAttribute(testPlan, "testclass", "TestPlan");
            ensureAttribute(testPlan, "enabled", "true");
        }
    }

    public void copyNormalized(Path source, Path target) {
        try {
            normalizeToPath(source, target);
        } catch (ScriptValidationException exception) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception copyException) {
                throw exception;
            }
        }
    }

    private void ensureAttribute(Element element, String name, String value) {
        if (!element.hasAttribute(name) || element.getAttribute(name).isBlank()) {
            element.setAttribute(name, value);
        }
    }

    private Document parse(Path source) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(source.toFile());
    }

    private Document parse(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    private void write(Document document, Path target) throws Exception {
        for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                document.removeChild(node);
                break;
            }
        }
        TransformerFactory factory = TransformerFactory.newInstance();
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(target.toFile()));
    }

    private String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        var writer = new java.io.StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
