package com.yr.perftest.platform.script;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class JmeterScriptDom {
    private JmeterScriptDom() {
    }

    static String stepId(Element element, ScriptStepType type) {
        return stepPrefix(type) + "-" + elementPath(element);
    }

    static Element nextHashTree(Element element) {
        for (Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element sibling) {
                return "hashTree".equals(sibling.getTagName()) ? sibling : null;
            }
        }
        return null;
    }

    static ScriptStepType stepType(Element element) {
        return switch (element.getTagName()) {
            case "ThreadGroup", "kg.apc.jmeter.threads.SteppingThreadGroup" -> ScriptStepType.THREAD_GROUP;
            case "HTTPSamplerProxy" -> ScriptStepType.HTTP_REQUEST;
            case "CSVDataSet" -> ScriptStepType.CSV_DATA;
            case "Arguments" -> ScriptStepType.USER_PARAMS;
            case "HeaderManager" -> ScriptStepType.HEADER_CONFIG;
            case "ResponseAssertion" -> ScriptStepType.RESPONSE_ASSERTION;
            case "JSONPathAssertion" -> ScriptStepType.JSON_ASSERTION;
            default -> null;
        };
    }

    private static String stepPrefix(ScriptStepType type) {
        return switch (type) {
            case THREAD_GROUP -> "thread";
            case HTTP_REQUEST -> "http";
            case CSV_DATA -> "csv";
            case USER_PARAMS -> "vars";
            case HEADER_CONFIG -> "header";
            case RESPONSE_ASSERTION -> "assert";
            case JSON_ASSERTION -> "jsonassert";
            default -> "step";
        };
    }

    private static String elementPath(Element element) {
        List<Integer> indexes = new ArrayList<>();
        for (Node node = element; node instanceof Element current; node = current.getParentNode()) {
            Node parent = current.getParentNode();
            if (!(parent instanceof Element)) {
                break;
            }
            int index = 0;
            for (Node sibling = parent.getFirstChild(); sibling != null; sibling = sibling.getNextSibling()) {
                if (sibling instanceof Element) {
                    if (sibling == current) {
                        indexes.add(index);
                        break;
                    }
                    index++;
                }
            }
        }
        Collections.reverse(indexes);
        return indexes.stream().map(String::valueOf).reduce((left, right) -> left + "-" + right).orElse("0");
    }
}
