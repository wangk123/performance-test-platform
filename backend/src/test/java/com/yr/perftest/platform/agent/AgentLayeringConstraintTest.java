package com.yr.perftest.platform.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLayeringConstraintTest {
    private static final List<String> BOUNDED_QUERY_CONTROLLERS = List.of(
            "AgentAggregateController.java",
            "AgentFailureSampleController.java",
            "AgentMetricSeriesController.java",
            "AgentPrometheusController.java"
    );

    @Test
    void agentPackageDoesNotDependOnRepositoriesFilesOrPrometheus() throws IOException {
        Path root = Path.of("src/main/java/com/yr/perftest/platform/agent");
        assertThat(Files.isDirectory(root)).as("agent package exists").isTrue();

        Path executionPackage = root.resolve("execution");
        assertThat(BOUNDED_QUERY_CONTROLLERS)
                .allSatisfy(fileName -> assertThat(executionPackage.resolve(fileName))
                        .as(fileName + " exists")
                        .isRegularFile());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("import ")) {
                            continue;
                        }
                        if (trimmed.contains("Repository")
                                || trimmed.startsWith("import java.nio.file.")
                                || trimmed.contains("prometheus")
                                || trimmed.contains("Prometheus")) {
                            violations.add(path.getFileName() + ": " + trimmed);
                        }
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }

        assertThat(violations).isEmpty();
    }
}
