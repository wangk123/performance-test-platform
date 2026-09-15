package com.yr.perftest.platform.envcheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** env-probe 子命令调用方：testConnection 走 ping 探测；probeTarget 一次连接批量探测；probePlatform 本机直跑。 */
@Component
public class EnvProbeClient {

    /** 批量探测 waitFor 超时上限（秒）：超时按探针数线性缩放，封顶防长批次无限等待。 */
    private static final long MAX_PROBE_TIMEOUT_SECONDS = 120;

    private final ObjectMapper objectMapper;
    private final EnvCheckProperties properties;
    private final Path runnerEntry;

    public EnvProbeClient(
            ObjectMapper objectMapper,
            @Value("${platform.remote-runner.path:remote-runner/remote_jmeter_runner/main.py}") String runnerPath,
            EnvCheckProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.runnerEntry = resolveRunnerEntry(runnerPath);
    }

    /** 便捷构造器：单测不依赖 Spring（缺省 runner 路径 + 缺省配置）。 */
    public EnvProbeClient() {
        this(new ObjectMapper(), "remote-runner/remote_jmeter_runner/main.py", new EnvCheckProperties());
    }

    /** 便捷构造器：单测注入自定义配置（缺省 runner 路径 + 缺省 ObjectMapper）。 */
    public EnvProbeClient(EnvCheckProperties properties) {
        this(new ObjectMapper(), "remote-runner/remote_jmeter_runner/main.py", properties);
    }

    public EnvCheckCredentialService.ConnectionTest testConnection(EnvCheckCredentialService.ResolvedCredential credential) {
        Map<String, Object> payload = basePayload(credential);
        payload.put("probe", "echo ok");
        Path keyFile = null;
        try {
            keyFile = applyAuth(payload, credential);
            return runEnvProbe(payload);
        } catch (Exception exception) {
            return new EnvCheckCredentialService.ConnectionTest(false, messageOf(exception));
        } finally {
            deleteQuietly(keyFile);
        }
    }

    /** TARGET 通道：多探测脚本一次 env-probe 连接执行；python 失败（ok=false）抛 IllegalStateException，由编排层转 WARNING。 */
    public List<ProbeOutcome> probeTarget(EnvCheckCredentialService.ResolvedCredential credential, List<ProbeCommand> commands) {
        Map<String, Object> payload = basePayload(credential);
        payload.put("probes", commands.stream()
                .map(command -> Map.of("id", command.id(), "script", command.script()))
                .toList());
        Path keyFile = null;
        try {
            keyFile = applyAuth(payload, credential);
            return probeOutcomes(payload);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(messageOf(exception), exception);
        } finally {
            deleteQuietly(keyFile);
        }
    }

    /** PLATFORM 通道：平台本机直跑完整命令（探测项自带命令字符串），只加超时与收输出。 */
    public ProbeOutcome probePlatform(String host, int port, String command) {
        Path outputPath = null;
        try {
            outputPath = Files.createTempFile("env-probe-platform-", ".log");
            Process process = new ProcessBuilder(command.split("\\s+"))
                    .redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile())
                    .start();
            boolean finished = process.waitFor(properties.getProbeTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("platform probe 超时（" + properties.getProbeTimeoutSeconds() + "s）");
            }
            return new ProbeOutcome("platform", process.exitValue(),
                    Files.readString(outputPath, StandardCharsets.UTF_8));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(messageOf(exception), exception);
        } finally {
            deleteQuietly(outputPath);
        }
    }

    private Map<String, Object> basePayload(EnvCheckCredentialService.ResolvedCredential credential) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("host", credential.host());
        payload.put("sshPort", credential.sshPort());
        payload.put("sshUsername", credential.username());
        return payload;
    }

    /** 密码 / 密钥二选一写入 payload；密钥内容落临时 pem（0600）传路径，返回待清理文件。 */
    private Path applyAuth(Map<String, Object> payload, EnvCheckCredentialService.ResolvedCredential credential) throws Exception {
        if (credential.password() != null && !credential.password().isBlank()) {
            payload.put("sshPassword", credential.password());
        } else if (credential.keyMaterial() != null && !credential.keyMaterial().isBlank()) {
            Path keyFile = writeTempKey(credential.keyMaterial());
            payload.put("sshKeyPath", keyFile.toString());
            return keyFile;
        }
        return null;
    }

    private List<ProbeOutcome> probeOutcomes(Map<String, Object> payload) throws Exception {
        EnvProbeRun run = executeEnvProbe(payload, "env probe 超时");
        if (!run.ok()) {
            throw new IllegalStateException(run.message().isBlank() ? firstLine(run.raw()) : run.message());
        }
        List<ProbeOutcome> outcomes = new ArrayList<>();
        for (var item : objectMapper.readTree(run.raw()).path("results")) {
            outcomes.add(new ProbeOutcome(
                    item.path("id").asText(""),
                    item.path("code").asInt(-1),
                    item.path("output").asText("")));
        }
        return outcomes;
    }

    private EnvCheckCredentialService.ConnectionTest runEnvProbe(Map<String, Object> payload) {
        EnvProbeRun run = executeEnvProbe(payload, "连接测试超时");
        if (run.ok()) {
            return new EnvCheckCredentialService.ConnectionTest(true,
                    run.message().isBlank() ? "connected" : run.message());
        }
        return new EnvCheckCredentialService.ConnectionTest(false,
                run.message().isBlank() ? firstLine(run.raw()) : run.message());
    }

    /** env-probe 进程骨架：临时文件收 stdout + waitFor 超时 destroy；返回解析后的 ok/message/原文。 */
    private EnvProbeRun executeEnvProbe(Map<String, Object> payload, String timeoutMessage) {
        try {
            List<String> args = new ArrayList<>();
            args.addAll(pythonCommand());
            args.add(runnerEntry.toString());
            args.add("env-probe");
            args.add(objectMapper.writeValueAsString(payload));
            Path outputPath = Files.createTempFile("env-probe-", ".log");
            try {
                Process process = new ProcessBuilder(args)
                        .redirectErrorStream(true)
                        .redirectOutput(outputPath.toFile())
                        .start();
                boolean finished = process.waitFor(batchTimeoutSeconds(payload), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new EnvProbeRun(false, timeoutMessage, "");
                }
                String output = Files.readString(outputPath, StandardCharsets.UTF_8);
                if (output.isBlank()) {
                    return new EnvProbeRun(false, "env probe 无输出（退出码 " + process.exitValue() + "）", output);
                }
                try {
                    var node = objectMapper.readTree(output);
                    boolean ok = process.exitValue() == 0 && node.path("ok").asBoolean(false);
                    return new EnvProbeRun(ok, node.path("message").asText(""), output);
                } catch (Exception parseException) {
                    return new EnvProbeRun(false, firstLine(output), output);
                }
            } finally {
                Files.deleteIfExists(outputPath);
            }
        } catch (Exception exception) {
            return new EnvProbeRun(false, messageOf(exception), "");
        }
    }

    /** 批探测超时按批不按条：单条 probeTimeoutSeconds × 探针数（下限 1 条），封顶 {@link #MAX_PROBE_TIMEOUT_SECONDS}。 */
    private int batchTimeoutSeconds(Map<String, Object> payload) {
        int probes = 1;
        if (payload.get("probes") instanceof List<?> list && !list.isEmpty()) {
            probes = list.size();
        }
        return (int) Math.min(MAX_PROBE_TIMEOUT_SECONDS, (long) properties.getProbeTimeoutSeconds() * probes);
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            // 临时文件清理失败可忽略
        }
    }

    /** env-probe 单次执行结果：ok 综合退出码与 payload、message 来自 payload、raw 为原始 stdout。 */
    private record EnvProbeRun(boolean ok, String message, String raw) {
    }

    private Path writeTempKey(String keyMaterial) throws Exception {
        Path keyFile = Files.createTempFile("env-probe-key-", ".pem");
        Files.writeString(keyFile, keyMaterial, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(keyFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统（如 Windows）跳过权限设置
        }
        return keyFile;
    }

    private List<String> pythonCommand() {
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")
                && Files.exists(Path.of("/usr/bin/arch"))) {
            return List.of("/usr/bin/arch", "-arm64", "python3");
        }
        return List.of("python3");
    }

    private Path resolveRunnerEntry(String runnerEntry) {
        Path path = Path.of(runnerEntry);
        if (Files.exists(path)) {
            return path;
        }
        Path parentPath = Path.of("..").resolve(runnerEntry).normalize();
        return Files.exists(parentPath) ? parentPath : path;
    }

    private String firstLine(String output) {
        String line = output.lines().findFirst().orElse("").trim();
        return line.length() > 500 ? line.substring(0, 500) : line;
    }

    private String messageOf(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
