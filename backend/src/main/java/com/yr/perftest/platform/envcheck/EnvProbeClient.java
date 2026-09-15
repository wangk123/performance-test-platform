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

/** env-probe 子命令调用方：testConnection 走 ping 探测；probe 由 Task 6 实装。 */
@Component
public class EnvProbeClient {
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

    public EnvCheckCredentialService.ConnectionTest testConnection(EnvCheckCredentialService.ResolvedCredential credential) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("host", credential.host());
        payload.put("sshPort", credential.sshPort());
        payload.put("sshUsername", credential.username());
        Path keyFile = null;
        try {
            if (credential.password() != null && !credential.password().isBlank()) {
                payload.put("sshPassword", credential.password());
            } else if (credential.keyMaterial() != null && !credential.keyMaterial().isBlank()) {
                keyFile = writeTempKey(credential.keyMaterial());
                payload.put("sshKeyPath", keyFile.toString());
            }
            payload.put("probe", "echo ok");
            return runEnvProbe(payload);
        } catch (Exception exception) {
            return new EnvCheckCredentialService.ConnectionTest(false, messageOf(exception));
        } finally {
            if (keyFile != null) {
                try {
                    Files.deleteIfExists(keyFile);
                } catch (Exception ignored) {
                    // 临时密钥文件清理失败可忽略
                }
            }
        }
    }

    /** Task 6 实装：多探测脚本一次连接执行。 */
    public List<ProbeOutput> probe(EnvCheckCredentialService.ResolvedCredential credential, List<ProbeSpec> probes) {
        throw new IllegalStateException("env-probe 尚未实装（Task 6）");
    }

    private EnvCheckCredentialService.ConnectionTest runEnvProbe(Map<String, Object> payload) {
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
                boolean finished = process.waitFor(properties.getProbeTimeoutSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new EnvCheckCredentialService.ConnectionTest(false, "连接测试超时");
                }
                String output = Files.readString(outputPath, StandardCharsets.UTF_8);
                if (output.isBlank()) {
                    return new EnvCheckCredentialService.ConnectionTest(false, "env probe 无输出（退出码 " + process.exitValue() + "）");
                }
                try {
                    var node = objectMapper.readTree(output);
                    boolean ok = process.exitValue() == 0 && node.path("ok").asBoolean(false);
                    String message = node.path("message").asText("");
                    if (ok) {
                        return new EnvCheckCredentialService.ConnectionTest(true, message.isBlank() ? "connected" : message);
                    }
                    return new EnvCheckCredentialService.ConnectionTest(false, message.isBlank() ? firstLine(output) : message);
                } catch (Exception parseException) {
                    return new EnvCheckCredentialService.ConnectionTest(false, firstLine(output));
                }
            } finally {
                Files.deleteIfExists(outputPath);
            }
        } catch (Exception exception) {
            return new EnvCheckCredentialService.ConnectionTest(false, messageOf(exception));
        }
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
