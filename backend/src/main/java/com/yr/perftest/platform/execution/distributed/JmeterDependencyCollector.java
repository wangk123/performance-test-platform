package com.yr.perftest.platform.execution.distributed;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JmeterDependencyCollector {
    public List<JmeterDependencyFile> collect(Path testPlanPath) {
        Path baseDir = testPlanPath.getParent();
        if (baseDir == null) {
            return List.of();
        }
        Map<String, JmeterDependencyFile> files = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(testPlanPath.toFile());
            NodeList properties = document.getElementsByTagName("stringProp");
            for (int index = 0; index < properties.getLength(); index++) {
                Element property = (Element) properties.item(index);
                String name = property.getAttribute("name").toLowerCase(Locale.ROOT);
                if (!name.contains("filename") && !name.contains("filepath") && !name.endsWith(".path")) {
                    continue;
                }
                String value = property.getTextContent();
                dependency(baseDir, value).forEach(file -> files.put(file.targetPath(), file));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return new ArrayList<>(files.values());
    }

    private List<JmeterDependencyFile> dependency(Path baseDir, String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return List.of();
        }
        Path path = Path.of(value.trim());
        if (path.isAbsolute()) {
            return List.of();
        }
        Path source = baseDir.resolve(path).normalize();
        if (!source.startsWith(baseDir.normalize()) || !Files.isRegularFile(source)) {
            return List.of();
        }
        return List.of(new JmeterDependencyFile(source.toString(), path.toString()));
    }
}
