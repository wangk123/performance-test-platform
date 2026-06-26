package com.yr.perftest.platform.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JmeterCommandExecutor {
    private final String jmeterExecutable;
    private final String jmeterJavaHome;

    public JmeterCommandExecutor(
            @Value("${platform.jmeter.executable:jmeter}") String jmeterExecutable,
            @Value("${platform.jmeter.java-home:}") String jmeterJavaHome
    ) {
        this.jmeterExecutable = jmeterExecutable;
        this.jmeterJavaHome = jmeterJavaHome;
    }

    public int execute(
            Path testPlanPath,
            Path discardPath,
            Path logPath,
            ExecutionConfig config
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(jmeterExecutable);
        command.add("-n");
        command.add("-t");
        command.add(testPlanPath.toString());
        command.add("-l");
        command.add(discardPath.toString());
        command.add("-j");
        command.add(logPath.toString());
        jmeterProperties(config).forEach((key, value) -> command.add("-J" + key + "=" + value));

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        if (jmeterJavaHome != null && !jmeterJavaHome.isBlank()) {
            processBuilder.environment().put("JAVA_HOME", jmeterJavaHome);
            processBuilder.environment().put("PATH", jmeterJavaHome + "/bin:" + processBuilder.environment().get("PATH"));
        }
        Process process = processBuilder.start();
        return process.waitFor();
    }

    private Map<String, String> jmeterProperties(ExecutionConfig config) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("threads", String.valueOf(config.threads()));
        properties.put("loops", String.valueOf(config.loops()));
        properties.put("duration", String.valueOf(config.duration()));
        properties.put("rampUp", String.valueOf(config.rampUp()));
        properties.putAll(config.jmeterProperties());
        return properties;
    }
}
