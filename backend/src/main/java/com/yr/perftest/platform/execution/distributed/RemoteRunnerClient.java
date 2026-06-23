package com.yr.perftest.platform.execution.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RemoteRunnerClient {
    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final Path runnerEntry;
    private final Duration connectTimeout;

    public RemoteRunnerClient(
            ObjectMapper objectMapper,
            @Value("${platform.distributed.runner.python:python3}") String pythonExecutable,
            @Value("${platform.distributed.runner.entry:./remote-runner/remote_jmeter_runner/main.py}") String runnerEntry,
            @Value("${platform.distributed.runner.connect-timeout-seconds:120}") long connectTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.pythonExecutable = pythonExecutable;
        this.runnerEntry = resolveRunnerEntry(runnerEntry);
        this.connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
    }

    public RemoteRunnerResult checkNode(PersistentExecutionNodeRecord node) {
        return run("check-node", nodePayload(node));
    }

    public RemoteRunnerResult installKey(Map<String, Object> payload) {
        return run("install-key", payload);
    }

    public RemoteRunnerResult startRun(Map<String, Object> payload) {
        return run("start-run", payload);
    }

    public RemoteRunnerResult pollRun(Map<String, Object> payload) {
        return run("poll-run", payload);
    }

    public RemoteRunnerResult collectRun(Map<String, Object> payload) {
        return run("collect-run", payload);
    }

    public RemoteRunnerResult stopRun(Map<String, Object> payload) {
        return run("stop-run", payload);
    }

    private RemoteRunnerResult run(String command, Map<String, Object> payload) {
        try {
            List<String> args = new ArrayList<>();
            args.addAll(pythonCommand());
            args.add(runnerEntry.toString());
            args.add(command);
            args.add(objectMapper.writeValueAsString(payload));
            Path outputPath = Files.createTempFile("remote-runner-", ".log");
            try {
                Process process = new ProcessBuilder(args)
                        .redirectErrorStream(true)
                        .redirectOutput(outputPath.toFile())
                        .start();
                boolean finished = process.waitFor(connectTimeout.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return RemoteRunnerResult.failed("remote runner connect timeout");
                }
                String output = Files.readString(outputPath, StandardCharsets.UTF_8);
                if (output.isBlank()) {
                    return new RemoteRunnerResult(process.exitValue() == 0, process.exitValue(), "", "");
                }
                try {
                    RemoteRunnerResult result = objectMapper.readValue(output, RemoteRunnerResult.class);
                    return new RemoteRunnerResult(result.ok(), result.exitCode(), result.message(), result.log());
                } catch (Exception exception) {
                    return RemoteRunnerResult.failed(output.length() > 2000 ? output.substring(0, 2000) : output);
                }
            } finally {
                Files.deleteIfExists(outputPath);
            }
        } catch (Exception exception) {
            return RemoteRunnerResult.failed(exception.getMessage());
        }
    }

    private Map<String, Object> nodePayload(PersistentExecutionNodeRecord node) {
        return Map.of(
                "host", node.getHost(),
                "sshPort", node.getSshPort(),
                "sshUsername", node.getSshUsername(),
                "sshKeyPath", node.getSshKeyPath(),
                "remoteWorkDir", node.getRemoteWorkDir()
        );
    }

    private Path resolveRunnerEntry(String runnerEntry) {
        Path path = Path.of(runnerEntry);
        if (Files.exists(path)) {
            return path;
        }
        Path parentPath = Path.of("..").resolve(runnerEntry).normalize();
        return Files.exists(parentPath) ? parentPath : path;
    }

    private List<String> pythonCommand() {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")
                && Files.exists(Path.of("/usr/bin/arch"))) {
            return List.of("/usr/bin/arch", "-arm64", pythonExecutable);
        }
        return List.of(pythonExecutable);
    }
}
