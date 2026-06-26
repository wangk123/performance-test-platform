package com.yr.perftest.platform.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionFailureSummarizerTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresDockerContainerIdsInLog() throws Exception {
        Path logPath = tempDir.resolve("jmeter.log");
        Files.writeString(logPath, """
                worker 192.168.1.10: a7f5b8d1ec0fbc2a4af8f35261c49e1487ffe77809500c47a1b4de82d25dc86d
                controller 192.168.1.11: b7f5b8d1ec0fbc2a4af8f35261c49e1487ffe77809500c47a1b4de82d25dc86e
                """);

        String summary = ExecutionFailureSummarizer.summarize("finished", logPath);

        assertThat(summary).doesNotContain("a7f5b8d1ec0fbc2a4af8f35261c49e1487ffe77809500c47a1b4de82d25dc86d");
        assertThat(summary).contains("JMeter execution failed");
    }

    @Test
    void prefersJmeterErrorLine() throws Exception {
        Path logPath = tempDir.resolve("jmeter.log");
        Files.writeString(logPath, """
                2026-06-25 16:00:00,123 INFO o.a.j.JMeter: Loading file
                2026-06-25 16:00:01,456 ERROR o.a.j.JMeter: Error in NonGUIDriver java.lang.IllegalArgumentException: Problem loading XML from: '/test/plan.jmx'
                """);

        String summary = ExecutionFailureSummarizer.summarize("finished", logPath);

        assertThat(summary).contains("Error in NonGUIDriver");
    }

    @Test
    void usesControllerExitMessage() {
        String summary = ExecutionFailureSummarizer.summarize("JMeter controller exited with code 1", null);

        assertThat(summary).isEqualTo("JMeter controller exited with code 1");
    }
}
