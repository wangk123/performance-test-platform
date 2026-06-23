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
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterScriptPatcher {
    private final JmeterScriptRenderer renderer;
    private final JmeterScriptNormalizer normalizer;

    public JmeterScriptPatcher(JmeterScriptRenderer renderer, JmeterScriptNormalizer normalizer) {
        this.renderer = renderer;
        this.normalizer = normalizer;
    }

    public String patch(String content, List<ScriptStepDefinition> steps) {
        try {
            Document document = parseDocument(content);
            Element hashTree = testPlanHashTree(document);
            patchHashTree(document, hashTree, steps == null ? List.of() : steps);
            normalizer.normalize(document);
            return serialize(document);
        } catch (ScriptValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScriptValidationException("failed to patch JMeter script");
        }
    }

    private void patchHashTree(Document document, Element hashTree, List<ScriptStepDefinition> steps) throws Exception {
        if (hashTree == null) {
            return;
        }
        Map<String, ScriptStepDefinition> desired = new LinkedHashMap<>();
        for (ScriptStepDefinition step : steps) {
            desired.put(step.id(), step);
        }
        for (StepNode node : recognizedChildren(hashTree)) {
            ScriptStepDefinition step = desired.remove(node.id());
            if (step == null) {
                removePair(hashTree, node);
                continue;
            }
            RenderedPair pair = renderPair(step);
            Element patched = replaceElement(document, node.element(), pair);
            Element childHashTree = node.hashTree() == null ? appendEmptyHashTree(document, hashTree, patched) : node.hashTree();
            patchHashTree(document, childHashTree, step.children());
            if (step.stepType() == ScriptStepType.HTTP_REQUEST) {
                syncHttpHeaders(document, childHashTree, pair);
            }
        }
        for (ScriptStepDefinition step : steps) {
            if (desired.containsKey(step.id())) {
                appendPair(document, hashTree, step);
            }
        }
    }

    private List<StepNode> recognizedChildren(Element hashTree) {
        return childElements(hashTree).stream()
                .filter(element -> !"hashTree".equals(element.getTagName()))
                .filter(element -> !isEmbeddedHttpHeaderManager(element))
                .map(element -> {
                    ScriptStepType type = JmeterScriptDom.stepType(element);
                    return type == null
                            ? null
                            : new StepNode(element, JmeterScriptDom.nextHashTree(element), JmeterScriptDom.stepId(element, type));
                })
                .filter(StepNode.class::isInstance)
                .map(StepNode.class::cast)
                .toList();
    }

    private Element replaceElement(Document document, Element element, RenderedPair pair) {
        Element imported = (Element) document.importNode(pair.element(), true);
        element.getParentNode().replaceChild(imported, element);
        return imported;
    }

    private void syncHttpHeaders(Document document, Element hashTree, RenderedPair pair) {
        removeHeaderManagers(hashTree);
        if (pair.hashTree() == null) {
            return;
        }
        List<Element> renderedChildren = childElements(pair.hashTree());
        for (int index = 0; index < renderedChildren.size(); index++) {
            Element child = renderedChildren.get(index);
            if (!"HeaderManager".equals(child.getTagName())) {
                continue;
            }
            Node anchor = hashTree.getFirstChild();
            Element importedHeader = (Element) document.importNode(child, true);
            if (anchor == null) {
                hashTree.appendChild(importedHeader);
            } else {
                hashTree.insertBefore(importedHeader, anchor);
            }
            if (index + 1 < renderedChildren.size() && "hashTree".equals(renderedChildren.get(index + 1).getTagName())) {
                hashTree.insertBefore(
                        document.importNode(renderedChildren.get(index + 1), true),
                        importedHeader.getNextSibling()
                );
            }
            return;
        }
    }

    private void removeHeaderManagers(Element hashTree) {
        List<Element> children = childElements(hashTree);
        for (int index = 0; index < children.size(); index++) {
            Element child = children.get(index);
            if (!"HeaderManager".equals(child.getTagName())) {
                continue;
            }
            hashTree.removeChild(child);
            if (index + 1 < children.size() && "hashTree".equals(children.get(index + 1).getTagName())) {
                hashTree.removeChild(children.get(index + 1));
            }
            return;
        }
    }

    private boolean isEmbeddedHttpHeaderManager(Element element) {
        if (!"HeaderManager".equals(element.getTagName())) {
            return false;
        }
        Node parent = element.getParentNode();
        if (!(parent instanceof Element parentElement) || !"hashTree".equals(parentElement.getTagName())) {
            return false;
        }
        for (Node node = parentElement.getPreviousSibling(); node != null; node = node.getPreviousSibling()) {
            if (node instanceof Element sibling && "HTTPSamplerProxy".equals(sibling.getTagName())) {
                return true;
            }
        }
        return false;
    }

    private void appendPair(Document document, Element hashTree, ScriptStepDefinition step) throws Exception {
        RenderedPair pair = renderPair(step);
        hashTree.appendChild(document.importNode(pair.element(), true));
        hashTree.appendChild(document.importNode(pair.hashTree(), true));
    }

    private RenderedPair renderPair(ScriptStepDefinition step) throws Exception {
        if (step.stepType() == ScriptStepType.THREAD_GROUP) {
            return firstPair(testPlanHashTree(parseDocument(renderer.render(List.of(step)))));
        }
        if (step.stepType() == null) {
            throw new ScriptValidationException("unsupported step type: " + step.type());
        }
        return parseRenderedFragment(renderer.renderStepFragment(step));
    }

    private RenderedPair parseRenderedFragment(String fragment) throws Exception {
        Document document = parseDocument("<?xml version=\"1.0\" encoding=\"UTF-8\"?><fragment>" + fragment + "</fragment>");
        Element root = document.getDocumentElement();
        for (Element child : childElements(root)) {
            if ("hashTree".equals(child.getTagName())) {
                continue;
            }
            Element hashTree = JmeterScriptDom.nextHashTree(child);
            return new RenderedPair(child, hashTree == null ? document.createElement("hashTree") : hashTree);
        }
        throw new ScriptValidationException("failed to render JMeter step");
    }

    private RenderedPair firstPair(Element hashTree) {
        for (Element element : childElements(hashTree)) {
            if (!"hashTree".equals(element.getTagName())) {
                Element pairHashTree = JmeterScriptDom.nextHashTree(element);
                return new RenderedPair(element, pairHashTree == null ? element.getOwnerDocument().createElement("hashTree") : pairHashTree);
            }
        }
        throw new ScriptValidationException("failed to render JMeter step");
    }

    private void removePair(Element hashTree, StepNode node) {
        hashTree.removeChild(node.element());
        if (node.hashTree() != null && node.hashTree().getParentNode() == hashTree) {
            hashTree.removeChild(node.hashTree());
        }
    }

    private Element appendEmptyHashTree(Document document, Element hashTree, Element element) {
        Element childHashTree = document.createElement("hashTree");
        Node next = element.getNextSibling();
        if (next == null) {
            hashTree.appendChild(childHashTree);
        } else {
            hashTree.insertBefore(childHashTree, next);
        }
        return childHashTree;
    }

    private Element testPlanHashTree(Document document) {
        for (int index = 0; index < document.getElementsByTagName("TestPlan").getLength(); index++) {
            Element testPlan = (Element) document.getElementsByTagName("TestPlan").item(index);
            Element hashTree = JmeterScriptDom.nextHashTree(testPlan);
            if (hashTree != null) {
                return hashTree;
            }
        }
        throw new ScriptValidationException("script content is not a JMeter test plan");
    }

    private List<Element> childElements(Element element) {
        java.util.ArrayList<Element> elements = new java.util.ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                elements.add(child);
            }
        }
        return elements;
    }

    private Document parseDocument(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    private String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    private record StepNode(Element element, Element hashTree, String id) {
    }

    private record RenderedPair(Element element, Element hashTree) {
    }
}
